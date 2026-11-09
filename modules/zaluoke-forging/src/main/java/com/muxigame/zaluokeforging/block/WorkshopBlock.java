package com.muxigame.zaluokeforging.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Directional, placeable display block with collision matching its solid model parts. */
public final class WorkshopBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final MapCodec<WorkshopBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("design").forGetter(block -> block.design),
            propertiesCodec()).apply(instance, WorkshopBlock::new));
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
    public final String design;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public WorkshopBlock(String design, BlockBehaviour.Properties properties) {
        super(properties);
        this.design = design;
        VoxelShape shape = switch(design) {
            case "mold_stand" -> Block.box(1,0,1,15,4,15);
            case "placed_blank", "placed_mold" -> Block.box(1,0,1,15,3,15);
            default -> WorkshopShapes.north(design);
        };
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            shapes.put(direction, shape);
            VoxelShape[] rotated = {Shapes.empty()};
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> rotated[0] = Shapes.or(rotated[0],
                    Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
            shape = rotated[0].optimize();
        }
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<WorkshopBlock> codec() { return CODEC; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (design.equals("bellows_support")) {
            Direction clicked = context.getClickedFace();
            if (clicked.getAxis().isHorizontal() && isHeatSource(context.getLevel(), context.getClickedPos().relative(clicked.getOpposite()))) facing=clicked.getOpposite();
            else for (Direction direction:Direction.Plane.HORIZONTAL) {
                if (isHeatSource(context.getLevel(),context.getClickedPos().relative(direction))) { facing=direction; break; }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LIT); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (design.equals("mold_stand") && level.getBlockEntity(pos) instanceof WorkshopEntity machine && !machine.work().isEmpty())
            return Shapes.or(shapes.get(state.getValue(FACING)),Block.box(5,4,5,11,25,11),Block.box(1,25,1,15,28,15));
        if(design.equals("blacksmith_anvil") && level.getBlockEntity(pos) instanceof WorkshopEntity be && !be.work().isEmpty()) {
            double w=com.muxigame.zaluokeforging.forging.Workpiece.axe(be.work())?.85*5/16:com.muxigame.zaluokeforging.forging.ForgeTarget.WIDTH;
            boolean alongZ=state.getValue(FACING).getAxis()==Direction.Axis.Z;
            return Shapes.or(shapes.get(state.getValue(FACING)),Shapes.box(.5-(alongZ?w:.425),.886875,.5-(alongZ?.425:w),.5+(alongZ?w:.425),com.muxigame.zaluokeforging.forging.ForgeTarget.TOP,.5+(alongZ?.425:w)));
        }
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }

    private static boolean isHeatSource(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                || level.getBlockState(pos).is(com.muxigame.zaluokeforging.ModContent.HEARTH.get());
    }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorkshopEntity(pos,state);
    }
    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return !level.isClientSide && type == com.muxigame.zaluokeforging.ModContent.MACHINE.get()
                ? (world,pos,block,entity) -> WorkshopEntity.tick(world,pos,block,(WorkshopEntity)entity) : null;
    }
    public boolean allowsRefinerPlacement(net.minecraft.world.item.ItemStack stack, net.minecraft.world.phys.BlockHitResult hit) {
        return design.equals("blacksmith_hearth") && hit.getDirection()==Direction.UP
                && stack.is(com.muxigame.zaluokeforging.ModContent.REFINER.get().asItem());
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
            net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (allowsRefinerPlacement(stack,hit)) return net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        return level.getBlockEntity(pos) instanceof WorkshopEntity machine && machine.interact(player,hand,hit)
                ? net.minecraft.world.ItemInteractionResult.CONSUME : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        return level.getBlockEntity(pos) instanceof WorkshopEntity machine && machine.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)
                ? net.minecraft.world.InteractionResult.CONSUME : net.minecraft.world.InteractionResult.PASS;
    }
    @Override public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide && (design.equals("placed_blank") || design.equals("placed_mold"))
                && level.getBlockEntity(pos) instanceof WorkshopEntity machine) {
            machine.work(stack.copyWithCount(1));
            if (design.equals("placed_mold")) com.muxigame.zaluokeforging.forging.Workpiece.put(machine.work(),"Iron",com.muxigame.zaluokeforging.forging.Workpiece.capacity(machine.work()));
        }
    }
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        if ((design.equals("placed_blank") || design.equals("placed_mold")) && level.getBlockEntity(pos) instanceof WorkshopEntity machine)
            return machine.work().copy();
        return super.getCloneItemStack(level,pos,state);
    }
    @Override protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof WorkshopEntity machine && !level.isClientSide) machine.dropContents();
        super.onRemove(state,level,pos,next,moving);
    }
}
