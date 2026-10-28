package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.service.ModelConverterService;
import com.project.w_3d_world.features.model.storage.ModelFileValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class BlenderModelConverterServiceImpl implements ModelConverterService {

    private final String resolvedBlenderPath;
    private final boolean enabled;
    private final int timeoutSeconds;
    private final ModelFileValidator validator = new ModelFileValidator();

    public BlenderModelConverterServiceImpl(
            @Value("${app.ai.blender-path:}") String blenderPathProperty,
            @Value("${app.ai.conversion-enabled:true}") boolean enabled,
            @Value("${app.ai.conversion-timeout-seconds:60}") int timeoutSeconds
    ) {
        this.enabled = enabled;
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
        this.resolvedBlenderPath = detectBlenderPath(blenderPathProperty);
        if (this.resolvedBlenderPath != null) {
            log.info("Blender headless model converter configured at: {}", this.resolvedBlenderPath);
        } else {
            log.info("Blender headless model converter not detected; conversion fallback will report FORMAT_UNAVAILABLE");
        }
    }

    private static String detectBlenderPath(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured.trim());
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().toString();
            }
        }
        String env = System.getenv("BLENDER_PATH");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env.trim());
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().toString();
            }
        }
        List<String> commonPaths = List.of(
                "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe",
                "C:\\Program Files\\Blender Foundation\\Blender 4.2\\blender.exe",
                "C:\\Program Files\\Blender Foundation\\Blender 4.0\\blender.exe",
                "/usr/bin/blender",
                "/usr/local/bin/blender",
                "/opt/blender/blender"
        );
        for (String cp : commonPaths) {
            Path p = Path.of(cp);
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().toString();
            }
        }
        return null;
    }

    @Override
    public boolean isConversionAvailable() {
        return enabled && resolvedBlenderPath != null && Files.isRegularFile(Path.of(resolvedBlenderPath));
    }

    @Override
    public byte[] convertGlb(byte[] glbBytes, String targetFormat) {
        if (!isConversionAvailable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "FORMAT_UNAVAILABLE: Model export format '" + targetFormat + "' is not natively available and conversion service is not configured.");
        }
        if (glbBytes == null || glbBytes.length < 24) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid GLB model data for conversion.");
        }
        String fmt = targetFormat == null ? "" : targetFormat.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("STL", "OBJ", "FBX").contains(fmt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported conversion format: " + fmt);
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("3dw-blender-conv-");
            Path inputGlb = tempDir.resolve("input.glb");
            Files.write(inputGlb, glbBytes);

            Path outStl = tempDir.resolve("export.stl");
            Path outObj = tempDir.resolve("export.obj");
            Path outFbx = tempDir.resolve("export.fbx");
            Path scriptFile = tempDir.resolve("convert.py");

            String pythonScript = """
                    import bpy, sys, os
                    bpy.ops.wm.read_factory_settings(use_empty=True)
                    bpy.ops.import_scene.gltf(filepath=r'%s')
                    target = '%s'.lower()
                    if target == 'stl':
                        bpy.ops.wm.stl_export(filepath=r'%s', ascii_format=False)
                    elif target == 'obj':
                        bpy.ops.wm.obj_export(filepath=r'%s', export_materials=True)
                    elif target == 'fbx':
                        bpy.ops.export_scene.fbx(filepath=r'%s')
                    """.formatted(
                    inputGlb.toAbsolutePath().toString(),
                    fmt,
                    outStl.toAbsolutePath().toString(),
                    outObj.toAbsolutePath().toString(),
                    outFbx.toAbsolutePath().toString()
            );
            Files.writeString(scriptFile, pythonScript, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    resolvedBlenderPath,
                    "-b",
                    "--python",
                    scriptFile.toAbsolutePath().toString()
            );
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Model conversion timed out after " + timeoutSeconds + " seconds.");
            }
            if (process.exitValue() != 0) {
                log.error("Blender conversion failed (exit code {}): {}", process.exitValue(), output);
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Model conversion failed in conversion subprocess.");
            }

            return switch (fmt) {
                case "STL" -> {
                    if (!Files.isRegularFile(outStl) || Files.size(outStl) == 0) {
                        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Converted STL file was not generated.");
                    }
                    byte[] stlBytes = Files.readAllBytes(outStl);
                    validator.validateContent(outStl, new ModelFileValidator.Format("stl", "model/stl", ModelFileValidator.MODEL_LIMIT));
                    yield stlBytes;
                }
                case "FBX" -> {
                    if (!Files.isRegularFile(outFbx) || Files.size(outFbx) == 0) {
                        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Converted FBX file was not generated.");
                    }
                    byte[] fbxBytes = Files.readAllBytes(outFbx);
                    validator.validateContent(outFbx, new ModelFileValidator.Format("fbx", "application/octet-stream", ModelFileValidator.MODEL_LIMIT));
                    yield fbxBytes;
                }
                case "OBJ" -> {
                    if (!Files.isRegularFile(outObj) || Files.size(outObj) == 0) {
                        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Converted OBJ file was not generated.");
                    }
                    yield packageObjFolder(tempDir, outObj);
                }
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported format: " + fmt);
            };

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute GLB conversion to {}: {}", fmt, e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to convert 3D model: " + e.getMessage());
        } finally {
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }
    }

    private byte[] packageObjFolder(Path tempDir, Path outObj) throws IOException {
        Path outMtl = tempDir.resolve("export.mtl");
        List<Path> textures = new ArrayList<>();
        try (var stream = Files.list(tempDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
                    textures.add(p);
                }
            });
        }

        if (!Files.exists(outMtl) && textures.isEmpty()) {
            byte[] objBytes = Files.readAllBytes(outObj);
            validator.validateContent(outObj, new ModelFileValidator.Format("obj", "model/obj", ModelFileValidator.MODEL_LIMIT));
            return objBytes;
        }

        // Package into ZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Read and adjust obj file
            String objText = Files.readString(outObj, StandardCharsets.UTF_8);
            if (Files.exists(outMtl)) {
                objText = objText.replace("export.mtl", "model.mtl");
            }
            zos.putNextEntry(new ZipEntry("model.obj"));
            zos.write(objText.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            if (Files.exists(outMtl)) {
                String mtlText = Files.readString(outMtl, StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry("model.mtl"));
                zos.write(mtlText.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            for (Path tex : textures) {
                String entryName = "textures/" + tex.getFileName().toString();
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(tex, zos);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private void cleanupTempDir(Path tempDir) {
        try {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try { Files.deleteIfExists(file); } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try { Files.deleteIfExists(dir); } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {}
    }
}
