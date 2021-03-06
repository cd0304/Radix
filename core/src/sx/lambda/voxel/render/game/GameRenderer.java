package sx.lambda.voxel.render.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.collision.BoundingBox;
import sx.lambda.voxel.RadixClient;
import sx.lambda.voxel.api.RadixAPI;
import sx.lambda.voxel.api.events.render.EventEntityRender;
import sx.lambda.voxel.api.events.render.EventPostWorldRender;
import sx.lambda.voxel.block.Block;
import sx.lambda.voxel.render.Renderer;
import sx.lambda.voxel.util.Vec3i;
import sx.lambda.voxel.world.chunk.BlockStorage;
import sx.lambda.voxel.world.chunk.BlockStorage.CoordinatesOutOfBoundsException;
import sx.lambda.voxel.world.chunk.IChunk;

import java.text.DecimalFormat;

import static com.badlogic.gdx.graphics.GL20.*;

/**
 * Handles the rendering of the current world
 */
public class GameRenderer implements Renderer {

    private final RadixClient game;
    private BitmapFont debugTextRenderer;
    private boolean initted = false;
    private boolean calcFrustum;
    private long lastDynamicTextRerenderMS = 0;
    private Frustum frustum;
    private BitmapFontCache fpsRender,
            glInfoRender,
            positionRender,
            headingRender,
            chunkposRender,
            awrtRender,
            lightlevelRender,
            activeThreadsRender,
            selectedBlockRender,
            glDebugRender;

    private Texture[] blockBreakStages = new Texture[10];
    private Model blockBreakModel;
    private ModelInstance blockBreakModelInstance;
    private ModelBatch blockOverlayBatch;
    private ModelBatch entityBatch;

    private int blockBreakStage = 0;

    // GL debug counters
    private int lastDC, lastGLC, lastVTCS, lastTB, lastSS;
    private int curDC, curGLC, curVTCS, curTB, curSS;

    public GameRenderer(RadixClient game) {
        this.game = game;
    }

    @Override
    public void render() {
        if (!initted)
            init();

        prepareWorldRender();
        game.getWorld().render();
        RadixAPI.instance.getEventManager().push(new EventPostWorldRender());
        drawBlockSelection();
        renderEntities();

        curDC = GLProfiler.drawCalls - lastDC;
        curGLC = GLProfiler.calls - lastGLC;
        curVTCS = (int) GLProfiler.vertexCount.total - lastVTCS;
        curTB = GLProfiler.textureBindings - lastTB;
        curSS = GLProfiler.shaderSwitches - lastSS;

        lastDC = GLProfiler.drawCalls;
        lastGLC = GLProfiler.calls;
        lastVTCS = (int) GLProfiler.vertexCount.total;
        lastTB = GLProfiler.textureBindings;
        lastSS = GLProfiler.shaderSwitches;
    }

    @Override
    public void draw2d(SpriteBatch batch) {
        if (!initted)
            init();

        if (System.currentTimeMillis() - lastDynamicTextRerenderMS >= 250) { // Rerender the dynamic texts every quarter second
            createDynamicRenderers();
            lastDynamicTextRerenderMS = System.currentTimeMillis();
        }

        if (game.debugInfoEnabled()) {
            glInfoRender.draw(batch);
            fpsRender.draw(batch);
            positionRender.draw(batch);
            headingRender.draw(batch);
            chunkposRender.draw(batch);
            awrtRender.draw(batch);
            lightlevelRender.draw(batch);
            activeThreadsRender.draw(batch);
            selectedBlockRender.draw(batch);
            glDebugRender.draw(batch);
        }
    }

    @Override
    public void cleanup() {
        debugTextRenderer.dispose();

        for (Texture t : blockBreakStages) {
            t.dispose();
        }

        if (blockBreakModel != null) {
            blockBreakModel.dispose();
        }

        initted = false;
    }

    @Override
    public void init() {
        initted = true;

        frustum = game.getCamera().frustum;

        debugTextRenderer = new BitmapFont();
        fpsRender = debugTextRenderer.newFontCache();
        awrtRender = debugTextRenderer.newFontCache();
        glInfoRender = debugTextRenderer.newFontCache();
        positionRender = debugTextRenderer.newFontCache();
        headingRender = debugTextRenderer.newFontCache();
        chunkposRender = debugTextRenderer.newFontCache();
        lightlevelRender = debugTextRenderer.newFontCache();
        activeThreadsRender = debugTextRenderer.newFontCache();
        selectedBlockRender = debugTextRenderer.newFontCache();
        glDebugRender = debugTextRenderer.newFontCache();

        blockOverlayBatch = new ModelBatch(Gdx.files.internal("shaders/gdx/world.vert.glsl"),
                Gdx.files.internal("shaders/gdx/world.frag.glsl"));
        entityBatch = new ModelBatch(Gdx.files.internal("shaders/gdx/world.vert.glsl"),
                Gdx.files.internal("shaders/gdx/world.frag.glsl"));
        String destroyStageFmt = "textures/block/overlay/destroy_stage_%d.png";
        for (int stage = 0; stage < 10; stage++) {
            blockBreakStages[stage] = new Texture(Gdx.files.internal(String.format(destroyStageFmt, stage)));
        }
    }

    private void prepareWorldRender() {
        if (shouldCalcFrustum()) {
            game.getCamera().position.set(game.getPlayer().getPosition().getX(),
                    game.getPlayer().getPosition().getY() + game.getPlayer().getEyeHeight(),
                    game.getPlayer().getPosition().getZ());
            game.getCamera().up.set(0, 1, 0);
            game.getCamera().direction.set(0, 0, -1);
            game.getCamera().rotate(game.getPlayer().getRotation().getPitch(), 1, 0, 0);
            game.getCamera().rotate(-game.getPlayer().getRotation().getYaw(), 0, 1, 0);

            game.getCamera().update(true);
            calcFrustum = false;
        } else {
            game.getCamera().update();
        }
    }

    private void drawBlockSelection() {
        int curProgressInt = Math.round(RadixClient.getInstance().getPlayer().getBreakPercent() * 10) - 1;
        if ((blockBreakModel == null || blockBreakStage != curProgressInt) && curProgressInt >= 0) {
            if (blockBreakModel != null)
                blockBreakModel.dispose();

            blockBreakStage = curProgressInt;

            ModelBuilder builder = new ModelBuilder();
            blockBreakModel = builder.createBox(1f, 1f, 1f,
                    new Material(TextureAttribute.createDiffuse(blockBreakStages[blockBreakStage]),
                            new BlendingAttribute(),
                            FloatAttribute.createAlphaTest(0.25f)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates);
            blockBreakModelInstance = new ModelInstance(blockBreakModel);
        }

        Vec3i curBlk = RadixClient.getInstance().getSelectedBlock();
        if (curBlk != null && curProgressInt >= 0) {
            Gdx.gl.glPolygonOffset(100000, 2000000);
            blockOverlayBatch.begin(RadixClient.getInstance().getCamera());
            blockBreakModelInstance.transform.translate(curBlk.x + 0.5f, curBlk.y + 0.5f, curBlk.z + 0.5f);
            blockOverlayBatch.render(blockBreakModelInstance);
            blockBreakModelInstance.transform.translate(-(curBlk.x + 0.5f), -(curBlk.y + 0.5f), -(curBlk.z + 0.5f));
            blockOverlayBatch.end();
            Gdx.gl.glPolygonOffset(100000, -2000000);
        }
    }

    private void renderEntities() {
//        entityBatch.begin(RadixClient.getInstance().getCamera());
//        game.getWorld().getLoadedEntities().forEach((entityId, entity) -> {
//            if (!entity.equals(game.getPlayer()) && entity.getModel() != null) {
//                BoundingBox ebb = new BoundingBox();
//                entity.getModel().calculateBoundingBox(ebb);
//                if(frustum.boundsInFrustum(entity.getPosition().getX(), entity.getPosition().getY(), entity.getPosition().getZ(),
//                        ebb.getWidth()/2.0f, ebb.getDepth()/2.0f, ebb.getHeight()/2.0f)) {
//                    entity.render(entityBatch);
//                    RadixAPI.instance.getEventManager().push(new EventEntityRender(entity));
//                }
//            }
//        });
//        entityBatch.end();
    }

    public void calculateFrustum() {
        this.calcFrustum = true;
    }

    public boolean shouldCalcFrustum() {
        return calcFrustum;
    }

    public Frustum getFrustum() {
        return frustum;
    }

    private void createDynamicRenderers() {
        float currentHeight = 2;

        String glInfoStr = String.format("%s (%s) [%s]",
                Gdx.gl.glGetString(GL_RENDERER), Gdx.gl.glGetString(GL_VERSION), Gdx.gl.glGetString(GL_VENDOR));
        GlyphLayout glGl = glInfoRender.setText(glInfoStr, 0, 0);
        glInfoRender.setPosition((float) Gdx.graphics.getWidth() - glGl.width, (Gdx.graphics.getHeight() - currentHeight));
        currentHeight += debugTextRenderer.getLineHeight();

        String fpsStr = "FPS: " + String.valueOf(Gdx.graphics.getFramesPerSecond());
        if (RadixClient.getInstance().getSettingsManager().getVisualSettings().getNonContinuous().getValue()) {
            fpsStr = fpsStr.concat(" (NON-CONTINUOUS! INACCURATE!)");
            fpsRender.setColor(Color.RED);
        }

        GlyphLayout fpsGl = fpsRender.setText(fpsStr, 0, 0);
        fpsRender.setPosition((float) Gdx.graphics.getWidth() - fpsGl.width, (Gdx.graphics.getHeight() - currentHeight));
        currentHeight += debugTextRenderer.getLineHeight();

        DecimalFormat posFormat = new DecimalFormat("#.00");
        String coordsStr = String.format("(x,y,z): %s,%s,%s",
                posFormat.format(game.getPlayer().getPosition().getX()),
                posFormat.format(game.getPlayer().getPosition().getY()),
                posFormat.format(game.getPlayer().getPosition().getZ()));
        GlyphLayout posGl = positionRender.setText(coordsStr, 0, 0);
        positionRender.setPosition((float) Gdx.graphics.getWidth() - posGl.width, (Gdx.graphics.getHeight() - currentHeight));
        currentHeight += debugTextRenderer.getLineHeight();

        String chunk = String.format("Chunk (x,z): %s,%s",
                game.getWorld().getChunkPosition(game.getPlayer().getPosition().getX()),
                game.getWorld().getChunkPosition(game.getPlayer().getPosition().getZ()));
        GlyphLayout chunkGl = chunkposRender.setText(chunk, 0, 0);
        chunkposRender.setPosition((float) Gdx.graphics.getWidth() - chunkGl.width,
                (Gdx.graphics.getHeight() - currentHeight));
        currentHeight += debugTextRenderer.getLineHeight();

        String headingStr = String.format("(yaw,pitch): %s,%s",
                posFormat.format(game.getPlayer().getRotation().getYaw()),
                posFormat.format(game.getPlayer().getRotation().getPitch()));
        GlyphLayout headingGl = headingRender.setText(headingStr, 0, 0);
        headingRender.setPosition((float) Gdx.graphics.getWidth() - headingGl.width,
                (Gdx.graphics.getHeight() - currentHeight));
        currentHeight += debugTextRenderer.getLineHeight();

        int playerX = MathUtils.floor(game.getPlayer().getPosition().getX());
        int playerY = MathUtils.floor(game.getPlayer().getPosition().getY());
        int playerZ = MathUtils.floor(game.getPlayer().getPosition().getZ());
        IChunk playerChunk = game.getWorld().getChunk(playerX, playerZ);
        try {
            if (playerChunk != null) {
                String llStr = String.format("Light Level @ Feet: %d",
                        playerChunk.getSunlight(playerX & (game.getWorld().getChunkSize() - 1),
                                playerY, playerZ & (game.getWorld().getChunkSize() - 1)));
                GlyphLayout llGl = lightlevelRender.setText(llStr, 0, 0);
                lightlevelRender.setPosition((float) Gdx.graphics.getWidth() - llGl.width,
                        (Gdx.graphics.getHeight() - currentHeight));
                currentHeight += debugTextRenderer.getLineHeight();
            }
        } catch (BlockStorage.CoordinatesOutOfBoundsException ex) {
            ex.printStackTrace();
        }

        String threadsStr = "Active threads: " + Thread.activeCount();
        GlyphLayout threadsGl = activeThreadsRender.setText(threadsStr, 0, 0);
        activeThreadsRender.setPosition((float) Gdx.graphics.getWidth() - threadsGl.width,
                (Gdx.graphics.getHeight() - currentHeight));

        // Current looked-at block info. Draws next to the crosshair
        String currentBlockStr = "";
        Vec3i cbLoc = game.getSelectedBlock();
        if (cbLoc != null) {
            try {
                Block cbBlk = game.getWorld().getChunk(cbLoc.x, cbLoc.z).getBlock(
                        cbLoc.x & (game.getWorld().getChunkSize() - 1),
                        cbLoc.y,
                        cbLoc.z & (game.getWorld().getChunkSize() - 1)
                );
                if (cbBlk != null) {
                    currentBlockStr = String.format(
                            "%s (%d)\n" + // Name (id)
                                    "%d%%", // Breaking percentage
                            cbBlk.getHumanName(), cbBlk.getID(),
                            100 - Math.round(game.getPlayer().getBreakPercent() * 100));
                }
            } catch (CoordinatesOutOfBoundsException e) { // Shouldn't happen
                e.printStackTrace();
            }
        }
        selectedBlockRender.setText(currentBlockStr, 0, 0);
        selectedBlockRender.setPosition(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2);

        glDebugRender.setText(String.format("DC: %d, GLC: %d, VTCS: %d, TB: %d, SS: %d",
                        curDC, curGLC, curVTCS, curTB, curSS),
                0, debugTextRenderer.getLineHeight() + 45);
    }

}
