package com.example.realtime.infrastructure.adapter.in.socket;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.messages.PacketsMessage;
import com.corundumstudio.socketio.protocol.*;
import io.netty.channel.*;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.Map;

/** Offload ordered Socket.IO packet callbacks, including Firebase auth, from Netty I/O. */
public class WorkerPipeline extends SocketIOChannelInitializer {
    private final EventExecutorGroup workers;
    public WorkerPipeline(EventExecutorGroup workers) { this.workers = workers; }
    @Override protected void addSocketioHandlers(ChannelPipeline pipeline) {
        super.addSocketioHandlers(pipeline);
        var handler = (ChannelInboundHandler) pipeline.remove(PACKET_HANDLER);
        pipeline.addBefore(workers, AUTHORIZE_HANDLER, PACKET_HANDLER, new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                try { handler.channelRead(ctx, msg); }
                finally {
                    if (msg instanceof PacketsMessage packets) packets.getContent().release();
                }
            }
        });
        pipeline.addBefore(PACKET_HANDLER, "missingAuthGuard", new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof PacketsMessage packets) {
                    String data = packets.getContent().toString(CharsetUtil.UTF_8);
                    // netty-socketio 2.0.13 skips AuthTokenListener when CONNECT has no data.
                    // Reject that form before it can acknowledge a namespace connection.
                    for (String part : data.split("\u001e")) {
                        if (part.matches("40(?:/[^,]*,)?")) {
                            Packet error = new Packet(PacketType.MESSAGE, packets.getClient().getEngineIOVersion());
                            error.setSubType(PacketType.ERROR);
                            error.setData(Map.of("message", "UNAUTHORIZED"));
                            packets.getClient().send(error);
                            return;
                        }
                    }
                    // Transports own and release their frames after fireChannelRead returns.
                    // Retain the borrowed payload until the worker finishes decoding it.
                    packets.getContent().retain();
                }
                ctx.fireChannelRead(msg);
            }
        });
    }
}
