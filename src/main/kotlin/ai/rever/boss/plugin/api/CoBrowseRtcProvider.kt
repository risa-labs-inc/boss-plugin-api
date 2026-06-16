package ai.rever.boss.plugin.api

/**
 * Host capability for running a WebRTC peer on behalf of a co-browse session.
 *
 * Only the host can create a peer (it owns the embedded browser engine that
 * provides the WebRTC stack), so this is exposed to plugins as a provider. The
 * plugin owns signaling transport (its own WebSocket); this provider just turns
 * SDP/ICE into a live [CoBrowseRtcPeer] and surfaces remote data-channel input.
 */
interface CoBrowseRtcProvider {
    /**
     * Create a WebRTC peer that will answer the viewer's offer.
     *
     * The viewer is the offerer and creates the data channels; the host peer
     * answers. All callbacks may fire on a background thread — the plugin is
     * responsible for marshaling as needed.
     *
     * @param onAnswer  local SDP answer to relay to the viewer.
     * @param onIce     local ICE candidate to relay to the viewer (trickle).
     * @param onInput   a control message received on the input data channel
     *                  (same JSON the viewer would otherwise send over the socket).
     * @param onState   connection state: true once a data channel is open, false on close/fail.
     * @return a peer handle, or null if WebRTC is unavailable in this host.
     */
    fun createPeer(
        onAnswer: (String) -> Unit,
        onIce: (String) -> Unit,
        onInput: (String) -> Unit,
        onState: (Boolean) -> Unit,
    ): CoBrowseRtcPeer?
}

/** A live host-side WebRTC peer for one viewer. */
interface CoBrowseRtcPeer {
    /** Apply the viewer's SDP offer; triggers [CoBrowseRtcProvider.createPeer]'s onAnswer. */
    fun acceptOffer(sdp: String)

    /** Add a trickled ICE candidate from the viewer. */
    fun addRemoteIce(candidate: String)

    /** Send a DOM event (rrweb JSON) to the viewer over the reliable data channel. */
    fun sendDom(json: String)

    /**
     * Start (or switch) streaming the live pixels of the tab whose page title is
     * [targetTitle] as a WebRTC video track — true fidelity for video/canvas/WebGL
     * that DOM-sync can't capture. No-op if video isn't supported.
     */
    fun startVideo(targetTitle: String) {}

    /** Stop the video track (DOM-sync continues). */
    fun stopVideo() {}

    /** Tear the peer down and release its resources. */
    fun close()
}
