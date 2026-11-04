package com.project.w_3d_world.features.model;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service đã chuyển sang truy vấn hàng loạt (tránh N+1 khi DB ở xa). Các test cũ vẫn giả lập
 * từng truy vấn đơn lẻ (findTop…, findPrimaryByModelVersionId, findByModelVersionIdOrderByFileIdAsc, findAll);
 * helper này nối các truy vấn hàng loạt mới về đúng các stub đó để test giữ nguyên dữ liệu và kỳ vọng.
 * Dùng lenient() vì không phải test nào cũng đi qua mọi truy vấn.
 */
public final class BatchRepositoryStubs {

    private BatchRepositoryStubs() {}

    @SuppressWarnings("unchecked")
    public static void bridge(Model3DRepository models, ModelVersionRepository versions, ModelFileRepository files) {
        lenient().when(models.findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(anyInt())).thenAnswer(inv -> {
            Integer owner = inv.getArgument(0);
            List<Model3D> all = models.findAll();
            return (all == null ? List.<Model3D>of() : all).stream()
                    .filter(m -> Objects.equals(m.getOwnerUserId(), owner) && m.getDeletedAt() == null)
                    .sorted(Comparator.comparing(Model3D::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        });
        lenient().when(versions.findLatestByModelIdIn(anyCollection())).thenAnswer(inv ->
                ((Collection<Integer>) inv.getArgument(0)).stream()
                        .map(id -> Optional.ofNullable(versions.findTopByModelIdOrderByVersionNumberDesc(id)).flatMap(o -> o))
                        .flatMap(Optional::stream)
                        .toList());
        lenient().when(files.findByModelVersionIdInOrderByFileIdAsc(anyCollection())).thenAnswer(inv ->
                ((Collection<Integer>) inv.getArgument(0)).stream()
                        .flatMap(id -> {
                            List<ModelFile> list = files.findByModelVersionIdOrderByFileIdAsc(id);
                            return list == null ? java.util.stream.Stream.<ModelFile>empty() : list.stream();
                        })
                        .sorted(Comparator.comparing(ModelFile::getFileId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList());
        lenient().when(files.findPrimaryByModelVersionIdIn(anyCollection())).thenAnswer(inv ->
                ((Collection<Integer>) inv.getArgument(0)).stream()
                        .map(id -> Optional.ofNullable(files.findPrimaryByModelVersionId(id)).flatMap(o -> o))
                        .flatMap(Optional::stream)
                        .toList());
    }
}
