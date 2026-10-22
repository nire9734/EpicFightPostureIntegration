package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.BasicModelPart;
import net.minecraft.client.model.EntityModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Optional Lionfish model snapshot. Kept separate from SourceModelPreparation
 * so clients without Lionfish never resolve these classes during normal renders.
 */
final class LionfishSourceModelState {
    static List<Runnable> capture(EntityModel<?> source) {
        AdvancedEntityModel<?> model = (AdvancedEntityModel<?>) source;
        List<Runnable> states = new ArrayList<>();
        Set<BasicModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AdvancedModelBox part : model.getAllParts()) {
            capturePart(part, visited, states);
        }
        return states;
    }

    private static void capturePart(BasicModelPart part, Set<BasicModelPart> visited,
                                    List<Runnable> states) {
        if (part == null || !visited.add(part)) {
            return;
        }
        if (part instanceof AdvancedModelBox advanced) {
            states.add(AdvancedState.capture(advanced));
            if (advanced.childModels != null) {
                for (BasicModelPart child : advanced.childModels) {
                    capturePart(child, visited, states);
                }
            }
        } else {
            states.add(BasicState.capture(part));
        }
    }

    private record BasicState(BasicModelPart part, float x, float y, float z,
                              float rx, float ry, float rz,
                              float sx, float sy, float sz, boolean shown)
            implements Runnable {
        static BasicState capture(BasicModelPart part) {
            return new BasicState(part,
                    part.rotationPointX, part.rotationPointY, part.rotationPointZ,
                    part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ,
                    part.xScale, part.yScale, part.zScale, part.showModel);
        }

        @Override
        public void run() {
            part.rotationPointX = x;
            part.rotationPointY = y;
            part.rotationPointZ = z;
            part.rotateAngleX = rx;
            part.rotateAngleY = ry;
            part.rotateAngleZ = rz;
            part.xScale = sx;
            part.yScale = sy;
            part.zScale = sz;
            part.showModel = shown;
        }
    }

    /** Captures regular pose as well as mutable default-pose and visibility data. */
    private static final class AdvancedState implements Runnable {
        private final AdvancedModelBox part;
        private final float[] values;
        private final boolean shown;
        private final boolean hidden;
        private final boolean scaleChildren;

        private AdvancedState(AdvancedModelBox part, float[] values) {
            this.part = part;
            this.values = values;
            this.shown = part.showModel;
            this.hidden = part.isHidden();
            this.scaleChildren = part.scaleChildren;
        }

        static AdvancedState capture(AdvancedModelBox part) {
            return new AdvancedState(part, new float[]{
                    part.rotationPointX, part.rotationPointY, part.rotationPointZ,
                    part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ,
                    part.xScale, part.yScale, part.zScale,
                    part.offsetX, part.offsetY, part.offsetZ,
                    part.defaultPositionX, part.defaultPositionY, part.defaultPositionZ,
                    part.defaultRotationX, part.defaultRotationY, part.defaultRotationZ,
                    part.defaultscaleX, part.defaultscaleY, part.defaultscaleZ,
                    part.defaultOffsetX, part.defaultOffsetY, part.defaultOffsetZ
            });
        }

        @Override
        public void run() {
            int i = 0;
            part.rotationPointX = values[i++];
            part.rotationPointY = values[i++];
            part.rotationPointZ = values[i++];
            part.rotateAngleX = values[i++];
            part.rotateAngleY = values[i++];
            part.rotateAngleZ = values[i++];
            part.xScale = values[i++];
            part.yScale = values[i++];
            part.zScale = values[i++];
            part.offsetX = values[i++];
            part.offsetY = values[i++];
            part.offsetZ = values[i++];
            part.defaultPositionX = values[i++];
            part.defaultPositionY = values[i++];
            part.defaultPositionZ = values[i++];
            part.defaultRotationX = values[i++];
            part.defaultRotationY = values[i++];
            part.defaultRotationZ = values[i++];
            part.defaultscaleX = values[i++];
            part.defaultscaleY = values[i++];
            part.defaultscaleZ = values[i++];
            part.defaultOffsetX = values[i++];
            part.defaultOffsetY = values[i++];
            part.defaultOffsetZ = values[i++];
            part.showModel = shown;
            part.setIsHidden(hidden);
            part.scaleChildren = scaleChildren;
        }
    }

    private LionfishSourceModelState() {
    }
}
