// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only Create content (DESIGN.md §9). Reached solely from {@link CreateContent#register} behind
 * a {@code Dist.CLIENT} check, so this class — and the rendering types it touches — is never
 * classloaded on a dedicated server (nor in the standalone jar, since it lives under
 * {@code compat.create}).
 */
public final class CreateClientContent {
    private CreateClientContent() {}

    public static void registerClient(IEventBus modBus) {
        modBus.addListener(CreateClientContent::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateContent.MECHANICAL_WEAVER_BE.get(), MechanicalWeaverRenderer::new);
    }
}
