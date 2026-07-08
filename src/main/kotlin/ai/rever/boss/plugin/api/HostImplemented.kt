package ai.rever.boss.plugin.api

/**
 * Marks an API type whose implementation lives in the BossConsole host.
 *
 * Because the host compiles these types in and serves them parent-first to
 * every plugin classloader, ANY member change to a `@HostImplemented` type is
 * a host-contract change: it ships only with a BossConsole release and must be
 * gated with `minBossVersion`.
 *
 * Types WITHOUT this annotation (plugin-to-plugin interfaces, data carriers)
 * ship via the boss-plugin-api jar alone and are gated with `minApiVersion`.
 *
 * Documentation-only — there is no runtime enforcement.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class HostImplemented
