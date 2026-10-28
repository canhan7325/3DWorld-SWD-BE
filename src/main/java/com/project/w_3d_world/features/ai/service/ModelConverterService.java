package com.project.w_3d_world.features.ai.service;

/**
 * Service for server-side 3D model conversion (e.g. GLB to STL, OBJ, FBX).
 */
public interface ModelConverterService {

    /**
     * Checks if server-side headless conversion (e.g. Blender) is configured and available.
     */
    boolean isConversionAvailable();

    /**
     * Converts a GLB model byte array into the requested format (STL, OBJ, or FBX).
     * For OBJ with textures/materials, returns a valid ZIP archive containing model.obj, model.mtl, and textures.
     *
     * @param glbBytes input valid GLB byte array
     * @param targetFormat requested target format: STL, OBJ, FBX
     * @return converted bytes
     */
    byte[] convertGlb(byte[] glbBytes, String targetFormat);
}
