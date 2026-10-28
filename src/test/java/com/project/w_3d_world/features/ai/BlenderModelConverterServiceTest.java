package com.project.w_3d_world.features.ai;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.service.impl.BlenderModelConverterServiceImpl;
import com.project.w_3d_world.features.ai.util.Procedural;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.storage.ModelFileValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BlenderModelConverterServiceTest {

    private final ModelFileValidator validator = new ModelFileValidator();

    @Test
    @DisplayName("BlenderConverter: converts GLB to STL, OBJ, and FBX when Blender is present")
    void convertGlb_AllFormats() throws Exception {
        BlenderModelConverterServiceImpl converter = new BlenderModelConverterServiceImpl(
                "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe",
                true,
                60
        );

        if (!converter.isConversionAvailable()) {
            // If running in environment without local blender, verify graceful unavailable handling
            ApiException ex = assertThrows(ApiException.class, () -> converter.convertGlb(new byte[30], "STL"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertTrue(ex.getMessage().contains("FORMAT_UNAVAILABLE"));
            return;
        }

        assertTrue(converter.isConversionAvailable());
        byte[] testGlb = Procedural.makeGlb("blender-test-cube");

        // 1. Convert to STL
        byte[] stlBytes = converter.convertGlb(testGlb, "STL");
        assertNotNull(stlBytes);
        assertTrue(stlBytes.length > 84);
        Path tempStl = Files.createTempFile("test-stl-", ".stl");
        try {
            Files.write(tempStl, stlBytes);
            ModelFileValidator.Format stlFmt = validator.validateMetadata("model.stl", "model/stl", ModelFileRole.SOURCE_MODEL);
            assertDoesNotThrow(() -> validator.validateContent(tempStl, stlFmt));
        } finally {
            Files.deleteIfExists(tempStl);
        }

        // 2. Convert to OBJ
        byte[] objBytes = converter.convertGlb(testGlb, "OBJ");
        assertNotNull(objBytes);
        assertTrue(objBytes.length > 0);

        // 3. Convert to FBX
        byte[] fbxBytes = converter.convertGlb(testGlb, "FBX");
        assertNotNull(fbxBytes);
        assertTrue(fbxBytes.length >= 27);
        Path tempFbx = Files.createTempFile("test-fbx-", ".fbx");
        try {
            Files.write(tempFbx, fbxBytes);
            ModelFileValidator.Format fbxFmt = validator.validateMetadata("model.fbx", "application/octet-stream", ModelFileRole.SOURCE_MODEL);
            assertDoesNotThrow(() -> validator.validateContent(tempFbx, fbxFmt));
        } finally {
            Files.deleteIfExists(tempFbx);
        }
    }

    @Test
    @DisplayName("BlenderConverter: disabled or invalid path fails closed with FORMAT_UNAVAILABLE")
    void convertGlb_DisabledOrInvalid_ThrowsUnavailable() {
        BlenderModelConverterServiceImpl disabledConverter = new BlenderModelConverterServiceImpl(
                "C:\\non-existent-blender.exe",
                false,
                10
        );
        assertFalse(disabledConverter.isConversionAvailable());

        ApiException ex = assertThrows(ApiException.class, () ->
                disabledConverter.convertGlb(Procedural.makeGlb("seed"), "STL"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("FORMAT_UNAVAILABLE"));
    }
}
