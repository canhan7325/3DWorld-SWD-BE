package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.service.impl.LocalModelStorageService;
import com.project.w_3d_world.features.model.service.impl.ModelUploadServiceImpl;
import com.project.w_3d_world.features.printing.service.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintRequestFileTransactionTest {
    @TempDir Path root;

    @Test
    void requestFailureRollsBackModelUploadAndRemovesExactFile() throws Exception {
        var models = mock(Model3DRepository.class);
        var versions = mock(ModelVersionRepository.class);
        var files = mock(ModelFileRepository.class);
        var requestService = mock(PrintRequestService.class);
        var storage = new LocalModelStorageService(root);
        var transactions = new RecordingTransactions();
        AtomicInteger nextId = new AtomicInteger(100);
        when(models.saveAndFlush(any())).thenAnswer(call -> {
            Model3D value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "modelId", nextId.incrementAndGet());
            return value;
        });
        when(versions.saveAndFlush(any())).thenAnswer(call -> {
            ModelVersion value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "modelVersionId", nextId.incrementAndGet());
            return value;
        });
        when(files.saveAndFlush(any())).thenAnswer(call -> {
            ModelFile value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "fileId", nextId.incrementAndGet());
            return value;
        });
        when(requestService.createDraft(any(), any())).thenThrow(new IllegalStateException("request failed"));
        ModelUploadService uploader = proxied(new ModelUploadServiceImpl(models, versions, files, storage), transactions);
        var create = proxied(new PrintRequestFileCreateService(uploader, requestService), transactions);
        var file = new MockMultipartFile("file", "scene.glb", "model/gltf-binary", new byte[] {1, 2, 3});

        assertThrows(IllegalStateException.class, () -> create.create(7, 3, true, file));
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
        try (var paths = Files.walk(root)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxied(T target, RecordingTransactions manager) {
        var factory = new ProxyFactory(target);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    private static final class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        private final Object transaction = new Object();
        private boolean active;

        @Override protected Object doGetTransaction() { return transaction; }
        @Override protected boolean isExistingTransaction(Object transaction) { return active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { active = true; }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; active = false; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; active = false; }
    }
}
