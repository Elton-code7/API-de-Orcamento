package br.com.orcamento.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http,@Value("${app.api-key}") String key) throws Exception {
        if(key==null || key.length()<24) throw new IllegalArgumentException("APP_API_KEY deve ter ao menos 24 caracteres.");
        var filter=new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                String supplied=req.getHeader("X-API-Key");
                if(supplied!=null && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("local-user",null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                }
                chain.doFilter(req,res);
            }
        };
        return http.csrf(csrf->csrf.disable()) // API stateless, sem cookies de autenticação.
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/health").permitAll().anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"error\":\"Informe X-API-Key válido.\"}");}))
            .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
    }
}
