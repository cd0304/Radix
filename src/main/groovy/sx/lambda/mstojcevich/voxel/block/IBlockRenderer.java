package sx.lambda.mstojcevich.voxel.block;

import sx.lambda.mstojcevich.voxel.util.gl.SpriteBatcher;
import sx.lambda.mstojcevich.voxel.world.chunk.IChunk;

import java.nio.FloatBuffer;

public interface IBlockRenderer {

    void renderVBO(IChunk chunk, float x, float y, float z, float[][][] lightLevels,
                   FloatBuffer vertexBuffer, FloatBuffer textureBuffer, FloatBuffer normalBuffer, FloatBuffer colorBuffer,
                   boolean shouldRenderTop, boolean shouldRenderBottom,
                   boolean shouldRenderLeft, boolean shouldRenderRight,
                   boolean shouldRenderFront, boolean shouldRenderBack);

    public void render2d(SpriteBatcher batcher, int x, int y, int width);

}
