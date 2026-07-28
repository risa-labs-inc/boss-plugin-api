package ai.rever.boss.plugin.api

/**
 * Marks an API type the host compiles in and serves parent-first.
 *
 * Because the host compiles these types in and serves them parent-first to
 * every plugin classloader, ANY member change to a `@HostImplemented` type is
 * a host-contract change: it ships only with a BossConsole release and must be
 * gated with `minBossVersion`.
 *
 * Note this covers any type the host compiles in, *whoever* implements it —
 * not only those the host implements. `BookmarkDataProvider` is implemented by
 * the bookmarks plugin, but the host's pinned copy is what every plugin
 * resolves, so member changes still need a host release. For such types the two
 * axes come apart: a member *existing* tracks `minBossVersion`, while its
 * *behaviour* tracks the implementing plugin's version. Gating on
 * `minBossVersion` alone gets you the default body.
 *
 * Types WITHOUT this annotation (plugin-to-plugin interfaces, data carriers)
 * ship via the boss-plugin-api jar alone and are gated with `minApiVersion`.
 *
 * Documentation-only — there is no runtime enforcement.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class HostImplemented
