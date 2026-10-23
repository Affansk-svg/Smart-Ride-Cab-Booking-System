package com.smartride.ride.config;

import com.smartride.ride.model.Ride;
import com.smartride.ride.repo.RideRepository;
import com.smartride.ride.security.AuthUser;
import com.smartride.ride.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;

/**
 * STOMP over SockJS at /ws.
 *  - CONNECT must carry "Authorization: Bearer <jwt>"
 *  - SUBSCRIBE to /topic/rides/{id}/** is allowed only for the ride's rider, its driver, or an admin
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtUtil jwt;
    private final RideRepository rides;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null || acc.getCommand() == null) return message;

                if (acc.getCommand() == StompCommand.CONNECT) {
                    String h = acc.getFirstNativeHeader("Authorization");
                    if (h == null || !h.startsWith("Bearer ")) throw new MessagingException("Missing token");
                    try {
                        AuthUser u = jwt.toUser(jwt.parse(h.substring(7)));
                        acc.setUser(new UsernamePasswordAuthenticationToken(u, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + u.role()))));
                    } catch (JwtException | IllegalArgumentException e) {
                        throw new MessagingException("Invalid token");
                    }
                } else if (acc.getCommand() == StompCommand.SUBSCRIBE) {
                    authorizeSubscription(acc.getUser(), acc.getDestination());
                }
                return message;
            }
        });
    }

    private void authorizeSubscription(Principal p, String dest) {
        if (dest == null || !dest.startsWith("/topic/rides/") || !(p instanceof UsernamePasswordAuthenticationToken t)
                || !(t.getPrincipal() instanceof AuthUser u))
            throw new MessagingException("Subscription denied");
        long id;
        try { id = Long.parseLong(dest.split("/")[3]); }
        catch (RuntimeException e) { throw new MessagingException("Bad destination"); }
        Ride r = rides.findById(id).orElseThrow(() -> new MessagingException("Ride not found"));
        boolean ok = "ADMIN".equals(u.role()) || r.getRiderId().equals(u.id())
                || (r.getDriverId() != null && r.getDriverId().equals(u.id()));
        if (!ok) throw new MessagingException("Subscription denied");
    }
}
