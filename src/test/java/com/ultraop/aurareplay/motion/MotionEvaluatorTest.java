package com.ultraop.aurareplay.motion;

import com.ultraop.aurareplay.actor.ActorTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotionEvaluatorTest {
    private static final ActorTransform ORIGIN = ActorTransform.origin(10, 20, 30, 90, 10);

    @Test
    void appliesOrderedTransformLayersWithoutTouchingSourceData() {
        MotionStack stack = new MotionStack();
        stack.add(MotionLayer.positionOffset("move", 2, 3, 4, 0, 100));
        stack.add(MotionLayer.rotationOffset("turn", 15, -5, 20, 0, 100));
        stack.add(MotionLayer.scale("scale", 2, 0, 100));

        MotionEvaluator.Evaluation result = new MotionEvaluator().evaluate(20, 100, ORIGIN, stack);

        assertEquals(12, result.transform().x());
        assertEquals(23, result.transform().y());
        assertEquals(34, result.transform().z());
        assertEquals(105, result.transform().yaw());
        assertEquals(5, result.transform().pitch());
        assertEquals(20, result.transform().roll());
        assertEquals(2, result.transform().scale());
        assertEquals(20, result.sourcePosition());
    }

    @Test
    void timingLayersSupportOffsetSpeedReverseTrimAndLoop() {
        MotionStack stack = new MotionStack();
        stack.add(MotionLayer.timeOffset("offset", 5, 0, 100));
        stack.add(MotionLayer.speed("speed", 2, 0, 100));
        stack.add(MotionLayer.reverse("reverse", 0, 100));
        stack.add(MotionLayer.loop("loop", 3, 0, 100));

        MotionEvaluator.Evaluation result = new MotionEvaluator().evaluate(10, 20, ActorTransform.origin(0, 0, 0, 0, 0), stack);

        assertEquals(9, result.sourcePosition());
        assertEquals(true, result.loop());
        assertEquals(true, result.reverse());
    }

    @Test
    void freezePosePinsSourcePosition() {
        MotionStack stack = new MotionStack();
        stack.add(MotionLayer.freezePose("freeze", 7, 0, 100));

        MotionEvaluator.Evaluation result = new MotionEvaluator().evaluate(40, 100, ORIGIN, stack);

        assertEquals(7, result.sourcePosition());
    }
}
