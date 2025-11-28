package org.example.sequritydemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

//    .authorizeHttpRequests(auth -> auth
//            .requestMatchers("/", "/info").permitAll() // 두개의 라우팅은 열고
//                .anyRequest().authenticated() // 나머지 모두는 로그인 필요
//            )

//    .authorizeHttpRequests(auth -> {
//        auth.requestMatchers("/", "/info").permitAll();
//        auth.anyRequest().authenticated();
//        return auth;
//    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/info", "/login", "/h2-console/**").permitAll() // 두개의 라우팅은 열고
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated() // 나머지 모두는 로그인 필요
            )
            .formLogin(form -> form
                .loginPage("/login")    // 커스텀 로그인 페이지 주소
                .defaultSuccessUrl("/dashboard", true)  // 로그인 성공시 이동할 페이지
                .failureUrl("/login")    // 로그인 실패시 이동할 페이지
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login") // 로그아웃 성공시 이동할 페이지
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            );

        http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

  //  @Bean
    //public UserDetailsService userDetailService() {
        // new User("user", "{noop}1234", "USER")
      //  UserDetails user = User.builder()
        //    .username("user")
          //  .password("{noop}1234")
            //.roles("USER")
            //.build();

        //UserDetails admin = User.builder()
          //  .username("admin")
            //.password("{noop}admin")
            //.roles("USER", "ADMIN")
            //.build();

        //return new InMemoryUserDetailsManager(user, admin);
    }