package com.tmw.backend.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmw.backend.common.entity.IdempotencyKeyRecord;
import com.tmw.backend.common.entity.IdempotencyKeyRecord.Status;
import com.tmw.backend.common.entity.VersionedEntity;
import com.tmw.backend.common.error.GlobalExceptionHandler;
import com.tmw.backend.common.filter.IdempotencyFilter;
import com.tmw.backend.common.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyAndETagIntegrationTest {

    private MockMvc mockMvc;
    private ConcurrentHashMap<String, IdempotencyKeyRecord> store;
    private TestMatchingController controller;

    @BeforeEach
    void setUp() {
        this.store = new ConcurrentHashMap<>();
        IdempotencyKeyRepository repository = createRepositoryProxy(store);
        this.controller = new TestMatchingController();
        IdempotencyFilter filter = new IdempotencyFilter(repository, new ObjectMapper());
        IfMatchInterceptor interceptor = new IfMatchInterceptor();

        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filter)
                .addInterceptors(interceptor)
                .build();
    }

    private static IdempotencyKeyRepository createRepositoryProxy(ConcurrentHashMap<String, IdempotencyKeyRecord> map) {
        AtomicLong seq = new AtomicLong(1);
        return (IdempotencyKeyRepository) Proxy.newProxyInstance(
                IdempotencyKeyRepository.class.getClassLoader(),
                new Class<?>[]{IdempotencyKeyRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByIdempotencyKey".equals(name) && args != null && args.length == 1) {
                        return Optional.ofNullable(map.get((String) args[0]));
                    }
                    if (("save".equals(name) || "saveAndFlush".equals(name)) && args != null && args.length == 1 && args[0] instanceof IdempotencyKeyRecord rec) {
                        if (rec.getId() == null) {
                            IdempotencyKeyRecord prev = map.putIfAbsent(rec.getIdempotencyKey(), rec);
                            if (prev != null) {
                                throw new DataIntegrityViolationException("Duplicate idempotency key");
                            }
                            rec.setId(seq.getAndIncrement());
                        } else {
                            map.put(rec.getIdempotencyKey(), rec);
                        }
                        return rec;
                    }
                    if ("delete".equals(name) && args != null && args.length == 1 && args[0] instanceof IdempotencyKeyRecord rec) {
                        if (rec.getIdempotencyKey() != null) {
                            map.remove(rec.getIdempotencyKey());
                        }
                        return null;
                    }
                    if ("flush".equals(name) || "deleteAll".equals(name)) {
                        if ("deleteAll".equals(name)) map.clear();
                        return null;
                    }
                    if ("count".equals(name)) return (long) map.size();
                    if ("findAll".equals(name)) return List.copyOf(map.values());
                    if (method.getReturnType().equals(Optional.class)) return Optional.empty();
                    if (method.getReturnType().equals(List.class)) return List.of();
                    return null;
                }
        );
    }

    @Test
    void shouldAllowGetWithoutIdempotencyKeyAndReturnETag() throws Exception {
        mockMvc.perform(get("/api/v1/test-matching/doc-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldReturn400WhenIdempotencyKeyIsMissingOnWrite() throws Exception {
        mockMvc.perform(post("/api/v1/test-matching")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    void shouldReplayCachedResponseWhenIdempotencyKeyIsRepeatedWithinTtl() throws Exception {
        String key = "idem-key-replay-001";

        mockMvc.perform(post("/api/v1/test-matching")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.executionCount").value(1));

        mockMvc.perform(post("/api/v1/test-matching")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executionCount").value(1))
                .andExpect(content().json("{\"executionCount\":1,\"version\":0}"));

        assertEquals(1, controller.executionCounter.get());
    }

    @Test
    void shouldReprocessWhenExistingIdempotencyKeyIsExpired() throws Exception {
        String key = "idem-key-expired-001";
        store.put(key, IdempotencyKeyRecord.builder()
                .id(99L)
                .idempotencyKey(key)
                .httpMethod("POST")
                .requestPath("/api/v1/test-matching")
                .status(Status.COMPLETED)
                .responseStatus(201)
                .responseBody("{\"executionCount\":1,\"version\":0}")
                .createdAt(Instant.now().minusSeconds(90_000))
                .expiresAt(Instant.now().minusSeconds(10))
                .build());

        mockMvc.perform(post("/api/v1/test-matching")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executionCount").value(1));

        assertEquals(1, controller.executionCounter.get());
    }

    @Test
    void shouldHandleConcurrentRequestsWithSameIdempotencyKeySafely() throws Exception {
        String key = "idem-key-concurrent-001";
        int threadCount = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                startGate.await(5, TimeUnit.SECONDS);
                MvcResult res = mockMvc.perform(post("/api/v1/test-matching")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
                return res.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(task));
        }
        startGate.countDown();

        int createdOrReplayCount = 0;
        int conflictOrReplayCount = 0;
        for (Future<Integer> future : futures) {
            int statusCode = future.get(10, TimeUnit.SECONDS);
            assertTrue(statusCode == 201 || statusCode == 409, "Unexpected status: " + statusCode);
            if (statusCode == 201) createdOrReplayCount++;
            if (statusCode == 409) conflictOrReplayCount++;
        }
        pool.shutdownNow();

        assertEquals(threadCount, createdOrReplayCount + conflictOrReplayCount);
        assertTrue(createdOrReplayCount >= 1);
        assertEquals(1, controller.executionCounter.get());
    }

    @Test
    void shouldAllowOneWinnerAndRejectStaleVersionInConcurrentETagUpdates() throws Exception {
        int threadCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int reqIdx = i;
            tasks.add(() -> {
                startGate.await(5, TimeUnit.SECONDS);
                MvcResult res = mockMvc.perform(patch("/api/v1/test-matching/doc-1/approve")
                                .header("Idempotency-Key", "idem-etag-race-" + reqIdx)
                                .header(HttpHeaders.IF_MATCH, "\"0\""))
                        .andReturn();
                return res.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(task));
        }
        startGate.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<Integer> future : futures) {
            int statusCode = future.get(10, TimeUnit.SECONDS);
            assertTrue(statusCode == 200 || statusCode == 412, "Unexpected status: " + statusCode);
            if (statusCode == 200) successCount++;
            if (statusCode == 412) conflictCount++;
        }
        pool.shutdownNow();

        assertEquals(1, successCount);
        assertEquals(threadCount - 1, conflictCount);
        assertEquals(1, controller.currentVersion.get());
    }

    static class SampleVersionedDoc extends VersionedEntity {
        private final Integer customVersion;
        SampleVersionedDoc(Integer version) {
            this.customVersion = version;
        }
        @Override
        public Integer getVersion() {
            return customVersion;
        }
    }

    @RestController
    static class TestMatchingController {
        private final AtomicInteger executionCounter = new AtomicInteger(0);
        private final AtomicInteger currentVersion = new AtomicInteger(0);
        private final Object lock = new Object();

        @GetMapping("/api/v1/test-matching/{id}")
        public ResponseEntity<Map<String, Object>> getDoc(@PathVariable String id) {
            int ver = currentVersion.get();
            SampleVersionedDoc doc = new SampleVersionedDoc(ver);
            String etag = doc.getETag() != null ? doc.getETag() : ETagSupport.format(ver);
            return ResponseEntity.ok()
                    .header(HttpHeaders.ETAG, etag)
                    .body(Map.of("id", id, "version", ver));
        }

        @PostMapping("/api/v1/test-matching")
        public ResponseEntity<Map<String, Object>> createDoc(@RequestBody(required = false) Map<String, Object> body) {
            int exec = executionCounter.incrementAndGet();
            int ver = currentVersion.get();
            return ResponseEntity.status(201)
                    .header(HttpHeaders.ETAG, ETagSupport.format(ver))
                    .body(Map.of("executionCount", exec, "version", ver));
        }

        @RequireIfMatch
        @PatchMapping("/api/v1/test-matching/{id}/approve")
        public ResponseEntity<Map<String, Object>> approveDoc(
                @PathVariable String id,
                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
        ) {
            synchronized (lock) {
                int ver = currentVersion.get();
                ETagSupport.verifyMatch(ifMatch, ver);
                int nextVer = currentVersion.incrementAndGet();
                return ResponseEntity.ok()
                        .header(HttpHeaders.ETAG, ETagSupport.format(nextVer))
                        .body(Map.of("id", id, "version", nextVer));
            }
        }
    }
}
