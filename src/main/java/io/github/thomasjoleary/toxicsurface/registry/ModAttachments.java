// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import com.mojang.serialization.Codec;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data attachments (DESIGN.md §2b). */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ToxicSurface.MODID);

    /**
     * Sludge band depth already applied to a chunk. Persisted with the chunk so the
     * water→sludge pass only converts the incremental band as escalation deepens it
     * (DESIGN.md §2b), and never re-converts.
     */
    public static final Supplier<AttachmentType<Integer>> APPLIED_SLUDGE_DEPTH = ATTACHMENT_TYPES.register(
            "applied_sludge_depth",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ModAttachments() {}
}
