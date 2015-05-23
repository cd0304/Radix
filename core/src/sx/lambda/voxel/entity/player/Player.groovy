package sx.lambda.voxel.entity.player

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import groovy.transform.CompileStatic
import sx.lambda.voxel.VoxelGameClient
import sx.lambda.voxel.api.BuiltInBlockIds
import sx.lambda.voxel.entity.EntityPosition
import sx.lambda.voxel.entity.EntityRotation
import sx.lambda.voxel.entity.LivingEntity
import sx.lambda.voxel.net.packet.shared.PacketPlayerPosition
import sx.lambda.voxel.world.IWorld
import sx.lambda.voxel.world.chunk.IChunk

@CompileStatic
class Player extends LivingEntity implements Serializable {

    private static final float WIDTH = 1, HEIGHT = 1.8f
    private static final float REACH = 4

    private transient boolean moved = false

    private static Model playerModel

    private int itemInHand = BuiltInBlockIds.STONE_ID

    //needed for serialization
    public Player() {
        this(new EntityPosition(0, 0, 0), new EntityRotation())

        if (playerModel == null && VoxelGameClient.instance != null) {
            playerModel = new ObjLoader().loadModel(Gdx.files.internal('entity/player.obj'));
        }
    }

    public Player(EntityPosition pos, EntityRotation rot) {
        super(playerModel,
                pos, rot, 1, HEIGHT)
    }

    public float getEyeHeight() {
        HEIGHT * 0.75
    }

    public float getReach() { REACH }

    public int getItemInHand() { itemInHand }

    public void setItemInHand(int block) { itemInHand = block }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (moved) {
            if (VoxelGameClient.instance.getServerChanCtx() != null) {
                VoxelGameClient.instance.getServerChanCtx().writeAndFlush(new PacketPlayerPosition(this.getPosition()))
            }
            moved = false
        }
    }

    public void setMoved(boolean moved) {
        this.moved = moved
    }

    public boolean hasMoved() {
        return this.moved;
    }

    @Override
    public Model getDefaultModel() {
        playerModel
    }

    public int getBlockInHead(IWorld world) {
        int x = (int)getPosition().x;
        int z = (int)getPosition().z;
        IChunk chunk = world.getChunkAtPosition(x, z)
        if (chunk != null) {
            return chunk.getBlockIdAtPosition(x, (int)(getPosition().y + HEIGHT), z)
        } else {
            return 0
        }
    }

}
