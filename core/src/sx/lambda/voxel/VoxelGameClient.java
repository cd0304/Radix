package sx.lambda.voxel;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import io.netty.channel.ChannelHandlerContext;
import pw.oxcafebabe.marcusant.eventbus.EventListener;
import pw.oxcafebabe.marcusant.eventbus.Priority;
import pw.oxcafebabe.marcusant.eventbus.exceptions.InvalidListenerException;
import sx.lambda.voxel.api.VoxelGameAPI;
import sx.lambda.voxel.api.events.EventEarlyInit;
import sx.lambda.voxel.api.events.EventWorldStart;
import sx.lambda.voxel.block.Block;
import sx.lambda.voxel.client.gui.GuiScreen;
import sx.lambda.voxel.client.gui.screens.IngameHUD;
import sx.lambda.voxel.client.gui.screens.MainMenu;
import sx.lambda.voxel.client.gui.transition.SlideUpAnimation;
import sx.lambda.voxel.client.gui.transition.TransitionAnimation;
import sx.lambda.voxel.client.net.ClientConnection;
import sx.lambda.voxel.entity.EntityPosition;
import sx.lambda.voxel.entity.EntityRotation;
import sx.lambda.voxel.entity.player.Player;
import sx.lambda.voxel.net.packet.client.PacketLeaving;
import sx.lambda.voxel.render.NotInitializedException;
import sx.lambda.voxel.render.Renderer;
import sx.lambda.voxel.render.game.GameRenderer;
import sx.lambda.voxel.settings.SettingsManager;
import sx.lambda.voxel.tasks.*;
import sx.lambda.voxel.texture.TextureManager;
import sx.lambda.voxel.util.PlotCell3f;
import sx.lambda.voxel.util.Vec3i;
import sx.lambda.voxel.util.gl.ShaderManager;
import sx.lambda.voxel.world.IWorld;
import sx.lambda.voxel.world.World;
import sx.lambda.voxel.world.chunk.IChunk;

import javax.swing.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import static com.badlogic.gdx.graphics.GL20.*;

public class VoxelGameClient extends ApplicationAdapter {

    public static final boolean DEBUG = false;
    public static final String GAME_TITLE = "VoxelTest";
    private static VoxelGameClient theGame;
    public int chunkRenderTimes = 0;
    public int numChunkRenders = 0;
    private SettingsManager settingsManager;
    private IWorld world;
    private Player player;
    private boolean done;
    private Vec3i selectedBlock;
    private Vec3i selectedNextPlace;
    private Queue<Runnable> glQueue = new ConcurrentLinkedDeque<Runnable>();
    private MainMenu mainMenu;
    private IngameHUD hud;
    private GuiScreen currentScreen;
    private TextureManager textureManager = new TextureManager();
    private ShaderManager shaderManager = new ShaderManager();
    private Renderer renderer;
    private TransitionAnimation transitionAnimation;
    private boolean remote;
    private ChannelHandlerContext serverChanCtx;
    private GameRenderer gameRenderer;
    private Texture blockTextureAtlas;
    private PerspectiveCamera camera;
    private OrthographicCamera hudCamera;
    private SpriteBatch guiBatch;
    private RepeatedTask[] handlers = new RepeatedTask[]{new WorldLoader(this), new MovementHandler(this), new EntityUpdater(this), new LightUpdater(this), new RotationHandler(this)};

    public static VoxelGameClient getInstance() {
        return theGame;
    }

    @Override
    public void create() {
        theGame = this;

        try {
            VoxelGameAPI.instance.registerBuiltinBlocks();
        } catch (VoxelGameAPI.BlockRegistrationException e) {
            e.printStackTrace();
        }
        try {
            VoxelGameAPI.instance.getEventManager().register(this);
        } catch (InvalidListenerException e) {
            e.printStackTrace();
        }
        VoxelGameAPI.instance.getEventManager().push(new EventEarlyInit());

        settingsManager = new SettingsManager();
        this.setupOGL();

        gameRenderer = new GameRenderer(this);
        hud = new IngameHUD();
        mainMenu = new MainMenu();
        setCurrentScreen(mainMenu);

        this.startHandlers();
    }

    private void startHandlers() {
        for (RepeatedTask r : handlers) {
            new Thread(r, r.getIdentifier()).start();
        }

    }

    private void setupOGL() {
        Gdx.gl.glEnable(GL_TEXTURE_2D);
        Gdx.gl.glEnable(GL_DEPTH_TEST);//Enable depth visibility check
        Gdx.gl.glDepthFunc(GL_LEQUAL);//How to test depth (less than or equal)

        camera = new PerspectiveCamera(90, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(10f, 150f, 10f);
        camera.lookAt(0, 0, 0);
        camera.near = 0.1f;
        camera.far = 300f;
        camera.update();
        hudCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        guiBatch = new SpriteBatch();
        guiBatch.setProjectionMatrix(hudCamera.combined);

        Gdx.input.setInputProcessor(new VoxelGameGdxInputHandler(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        done = true;
        if (isRemote() && this.serverChanCtx != null) {
            this.serverChanCtx.writeAndFlush(new PacketLeaving("Game closed"));
            this.serverChanCtx.disconnect();
        }

    }

    @Override
    public void render() {
        try {
            if (done) Gdx.app.exit();

            prepareNewFrame();

            runQueuedOGL();

            if (renderer != null) {
                renderer.render();
            }


            guiBatch.begin();
            if (renderer != null) {
                renderer.draw2d(guiBatch);
            }

            if (currentScreen != null) {
                currentScreen.render(world != null, guiBatch);
            }

            guiBatch.end();
        } catch (Exception e) {
            done = true;
            e.printStackTrace();
            Gdx.input.setCursorCatched(false);
            Gdx.app.exit();
        }

    }

    private void runQueuedOGL() {
        Runnable currentRunnable;
        while ((currentRunnable = glQueue.poll()) != null) {
            currentRunnable.run();
        }

    }

    private void prepareNewFrame() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.2f, 0.2f, 1.0f, 1.0f);
        Gdx.gl.glClear((int) GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void updateSelectedBlock() {
        PlotCell3f plotter = new PlotCell3f(0, 0, 0, 1, 1, 1);
        float x = player.getPosition().getX();
        float y = player.getPosition().getY() + player.getEyeHeight();
        float z = player.getPosition().getZ();
        float pitch = player.getRotation().getPitch();
        float yaw = player.getRotation().getYaw();
        float reach = player.getReach();

        float deltaX = (float) (Math.cos(Math.toRadians(pitch)) * Math.sin(Math.toRadians(yaw)));
        float deltaY = (float) (Math.sin(Math.toRadians(pitch)));
        float deltaZ = (float) (-Math.cos(Math.toRadians(pitch)) * Math.cos(Math.toRadians(yaw)));

        plotter.plot(new Vector3(x, y, z), new Vector3(deltaX, deltaY, deltaZ), (int) Math.ceil((double) reach * reach));
        Vec3i last = null;
        while (plotter.next()) {
            Vec3i v = plotter.get();
            Vec3i bp = new Vec3i(v.x, v.y, v.z);
            IChunk theChunk = world.getChunkAtPosition(bp);
            if (theChunk != null) {
                if (theChunk.getBlockAtPosition(bp) != null) {
                    selectedBlock = bp;
                    if (last != null) {
                        if (theChunk.getBlockAtPosition(last) == null) {
                            selectedNextPlace = last;
                        }

                    }

                    plotter.end();
                    return;

                }

                last = bp;
            }

        }

        selectedNextPlace = null;
        selectedBlock = null;
    }

    public void addToGLQueue(Runnable runnable) {
        glQueue.add(runnable);
    }

    public IWorld getWorld() {
        return world;
    }

    public Player getPlayer() {
        return player;
    }

    public TextureManager getTextureManager() {
        return textureManager;
    }

    public ShaderManager getShaderManager() {
        return shaderManager;
    }

    public boolean isDone() {
        return done;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public Vec3i getSelectedBlock() {
        return selectedBlock;
    }

    public Vec3i getNextPlacePos() {
        return selectedNextPlace;
    }

    public void startShutdown() {
        done = true;
    }

    private void setRenderer(Renderer renderer) {
        if (renderer == null) {
            if (this.renderer != null) {
                synchronized (this.renderer) {
                    this.renderer.cleanup();
                    this.renderer = null;
                }

            } else {
                this.renderer = null;
            }

        } else if (this.renderer == null) {
            this.renderer = renderer;
            this.renderer.init();
        } else if (!this.renderer.equals(renderer)) {
            synchronized (this.renderer) {
                this.renderer.cleanup();
                this.renderer = renderer;
                this.renderer.init();
            }

        }

    }

    public boolean isRemote() {
        return remote;
    }

    public ChannelHandlerContext getServerChanCtx() {
        return serverChanCtx;
    }

    public void setServerChanCtx(ChannelHandlerContext ctx) {
        serverChanCtx = ctx;
    }

    public void handleCriticalException(Exception ex) {
        ex.printStackTrace();
        this.done = true;
        Gdx.input.setCursorCatched(false);
        JOptionPane.showMessageDialog(null, GAME_TITLE + " crashed. " + String.valueOf(ex), GAME_TITLE + " crashed", JOptionPane.ERROR_MESSAGE);
    }

    public GuiScreen getCurrentScreen() {
        return currentScreen;
    }

    public void setCurrentScreen(GuiScreen screen) {
        if (currentScreen == null) {
            currentScreen = screen;
            screen.init();
        } else if (!currentScreen.equals(screen)) {
            synchronized (currentScreen) {
                currentScreen.finish();
                currentScreen = screen;
                screen.init();
            }

        }

        if (screen.equals(hud)) {
            Gdx.input.setCursorCatched(true);
        } else {
            Gdx.input.setCursorCatched(false);
        }

    }

    public IngameHUD getHud() {
        return this.hud;
    }

    public GameRenderer getGameRenderer() {
        return this.gameRenderer;
    }

    public void enterRemoteWorld(final String hostname, final short port) {
        enterWorld(new World(true, false), true);

        new Thread("Client Connection") {
            @Override
            public void run() {
                try {
                    new ClientConnection(hostname, port).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }.start();
    }

    public void enterLocalWorld(IWorld world) {
        enterWorld(world, false);
    }

    public void exitWorld() {

        addToGLQueue(new Runnable() {
            @Override
            public void run() {
                setCurrentScreen(mainMenu);
                setRenderer(null);
                getWorld().cleanup();
                if (isRemote()) {
                    if (getServerChanCtx() != null) {
                        getServerChanCtx().writeAndFlush(new PacketLeaving("Leaving"));
                        getServerChanCtx().disconnect();
                        setServerChanCtx(null);
                    }

                }

                world = null;
                remote = false;
                player = null;
            }

        });

    }

    private void enterWorld(final IWorld world, final boolean remote) {
        this.world = world;
        this.remote = remote;
        player = new Player(new EntityPosition(0, 256, 0), new EntityRotation(0, 0));
        glQueue.add(new Runnable() {
            @Override
            public void run() {
                setRenderer(getGameRenderer());
                getPlayer().init();
                world.addEntity(getPlayer());
                transitionAnimation = new SlideUpAnimation(getCurrentScreen(), 1000);
                transitionAnimation.init();
                setCurrentScreen(getHud());

                if (!remote) {
                    world.loadChunks(new EntityPosition(0, 0, 0), getSettingsManager().getVisualSettings().getViewDistance());
                }


                VoxelGameAPI.instance.getEventManager().push(new EventWorldStart());
                // Delays are somewhere in this function. Above here.
            }

        });
    }

    public Texture getBlockTextureAtlas() throws NotInitializedException {
        if (blockTextureAtlas == null) throw new NotInitializedException();
        return blockTextureAtlas;
    }

    @EventListener(Priority.LAST)
    public void onBlockRegister(EventEarlyInit event) {
        // Create a texture atlas for all of the blocks
        addToGLQueue(new Runnable() {
            @Override
            public void run() {
                Pixmap bi = new Pixmap(1024, 1024, Pixmap.Format.RGB888);
                bi.setColor(1, 1, 1, 1);
                final int BLOCK_TEX_SIZE = 32;
                for (Block b : VoxelGameAPI.instance.getBlocks()) {
                    int x = b.getID() * BLOCK_TEX_SIZE % bi.getWidth();
                    int y = b.getID() * BLOCK_TEX_SIZE / bi.getWidth();
                    Pixmap tex = new Pixmap(Gdx.files.internal(b.getTextureLocation()));
                    bi.drawPixmap(tex, x, y);
                    tex.dispose();
                }

                blockTextureAtlas = new Texture(bi);
                bi.dispose();
            }

        });
    }

    @Override
    public void resize(int width, int height) {
        hudCamera.setToOrtho(false, width, height);
        hudCamera.update();
        guiBatch.setProjectionMatrix(hudCamera.combined);

        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    public OrthographicCamera getHudCamera() {
        return hudCamera;
    }
}