package sx.lambda.voxel.client.gui;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public interface GuiScreen {
    /**
     * Initializes in an opengl context
     */
    void init();

    /**
     * Draws the screen
     *
     * @param inGame Whether the screen is being drawn inside a world
     */
    void render(boolean inGame, SpriteBatch guiBatch);

    /**
     * Called after a screen is no longer active.
     */
    void finish();

    void onMouseClick(int button, boolean up);
    void mouseMoved(int x, int y);

    void keyTyped(char c);
}
