package dev.nire9734.epicfightposture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.SpecialRenderFailures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs on the Forge test classpath, which provides PoseStack. */
class SpecialRenderGuardTest {
    @AfterEach
    void clear() {
        SpecialRenderFailures.clear();
    }

    @Test
    void failureInsideNestedPushesIsContainedAndTheStackRestored() {
        PoseStack stack = new PoseStack();
        stack.pushPose();
        PoseStack.Pose entry = stack.last();

        assertDoesNotThrow(() -> SpecialRenderGuard.render(null, stack, () -> {
            stack.pushPose();
            stack.pushPose();
            throw new IllegalStateException("third-party layer failed between push and pop");
        }));

        assertSame(entry, stack.last(), "stack is back at its entry depth");
        stack.popPose();
        assertTrue(stack.clear(), "the caller's own pop still balances the frame");
    }

    @Test
    void balancedRenderIsUntouched() {
        PoseStack stack = new PoseStack();
        PoseStack.Pose entry = stack.last();
        SpecialRenderGuard.render(null, stack, () -> {
            stack.pushPose();
            stack.popPose();
        });
        assertSame(entry, stack.last());
        assertFalse(SpecialRenderGuard.restore(stack, entry));
    }

    @Test
    void swallowedFailureThatLeftAPushIsAlsoRepaired() {
        PoseStack stack = new PoseStack();
        PoseStack.Pose entry = stack.last();
        SpecialRenderGuard.render(null, stack, stack::pushPose);
        assertSame(entry, stack.last());
    }

    @Test
    void fatalJvmErrorsAreNotSwallowed() {
        PoseStack stack = new PoseStack();
        assertThrows(StackOverflowError.class, () -> SpecialRenderGuard.render(null, stack, () -> {
            throw new StackOverflowError();
        }));
    }
}
