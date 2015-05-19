package sx.lambda.voxel.block;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder;

import java.nio.FloatBuffer;

public class WaterRenderer extends NormalBlockRenderer {

    public WaterRenderer(int blockID) {
        super(blockID);
    }

    @Override
    public void renderNorth(int x1, int y1, int x2, int y2, int z, float lightLevel, MeshBuilder builder) {
        // POSITIVE Z
        builder.setColor(lightLevel, lightLevel, lightLevel, 0.6f);
        builder.setUVRange(blockID / 100.0f, blockID / 100.0f, blockID / 100.0f, blockID / 100.0f);
        builder.rect(x1, y1, z,
                x2, y1, z,
                x2, y2, z,
                x1, y2, z,
                0, 0, 1);
    }

    @Override
    public void renderSouth(int x1, int y1, int x2, int y2, int z, float lightLevel, MeshBuilder builder) {
        // NEGATIVE Z
        builder.setColor(lightLevel, lightLevel, lightLevel, 0.6f);
        builder.setUVRange(blockID / 100.0f, blockID / 100.0f, blockID / 100.0f, blockID / 100.0f);
        builder.rect(x1, y2, z,
                x2, y2, z,
                x2, y1, z,
                x1, y1, z,
                0, 0, -1);
    }

    @Override
    public void renderWest(int z1, int y1, int z2, int y2, int x, float lightLevel, MeshBuilder builder) {
        // NEGATIVE X
        builder.setColor(lightLevel, lightLevel, lightLevel, 0.6f);
        builder.setUVRange(blockID / 100.0f, blockID / 100.0f, blockID / 100.0f, blockID / 100.0f);
        builder.rect(x, y1, z2,
                x, y2, z2,
                x, y2, z1,
                x, y1, z1,
                -1, 0, 0);
    }

    @Override
    public void renderEast(int z1, int y1, int z2, int y2, int x, float lightLevel, MeshBuilder builder) {
        // POSITIVE X
        builder.setColor(lightLevel, lightLevel, lightLevel, 0.6f);
        builder.setUVRange(blockID / 100.0f, blockID / 100.0f, blockID / 100.0f, blockID / 100.0f);
        builder.rect(x, y1, z1,
                x, y2, z1,
                x, y2, z2,
                x, y1, z2,
                1, 0, 0);
    }

    @Override
    public void renderTop(int x1, int z1, int x2, int z2, int y, float lightLevel, MeshBuilder builder) {
        // POSITIVE Y
        builder.setColor(lightLevel, lightLevel, lightLevel, 0.6f);
        builder.setUVRange(blockID / 100.0f, blockID / 100.0f, blockID / 100.0f, blockID / 100.0f);
        builder.rect(x1, y, z2,
                x2, y, z2,
                x2, y, z1,
                x1, y, z1,
                0, 1, 0);
    }

    @Override
    public void renderBottom(int x1, int z1, int x2, int z2, int y, float lightLevel, MeshBuilder builder) {
        // NEGATIVE Y
        builder.setColor(lightLevel, lightLevel, lightLevel, 0.6f);
        builder.setUVRange(blockID / 100.0f, blockID / 100.0f, blockID / 100.0f, blockID / 100.0f);
        builder.rect(x1, y, z1,
                x2, y, z1,
                x2, y, z2,
                x1, y, z2,
                0, -1, 0);
    }

}