# Spring Security 로직 동작 순서 완전 분석
## 1. 애플리케이션 시작 시 (Application Startup)

```
[애플리케이션 시작]
    ↓
[Spring Container 초기화]
    ↓
[@Component, @Service, @Configuration 스캔 및 Bean 등록]
    ↓
[SecurityConfig의 @Bean 메서드 실행]
    ├─→ SecurityFilterChain 생성 (URL 권한 규칙 설정)
    └─→ PasswordEncoder(BCryptPasswordEncoder) 생성
    ↓
[CustomUserDetailService 빈 등록]
    ↓
[DataInitializer.run() 자동 실행]
    ├─→ passwordEncoder.encode("1234") 호출
    ├─→ User 엔티티 생성 (username: "user", 암호화된 password, role: "ROLE_USER")
    ├─→ User 엔티티 생성 (username: "admin", 암호화된 password, role: "ROLE_ADMIN")
    ├─→ userRepository.save(user)
    └─→ userRepository.save(admin)
    ↓
[애플리케이션 준비 완료 - 요청 대기]
```

## 2. 사용자가 홈페이지 접속 (미인증 상태)
```
[브라우저] http://localhost:8080/ 요청
    ↓
[Spring Security Filter Chain 진입]
    ↓
[SecurityFilterChain의 authorizeHttpRequests 확인]
    ├─→ requestMatchers("/", "/info", "/login", "/h2-console/**").permitAll()
    ├─→ "/" 경로는 permitAll() → 인증 불필요
    └─→ 통과 ✓
    ↓
[HomeController.home() 메서드 실행]
    └─→ return "home"
    ↓
[ViewResolver가 templates/home.html 찾아서 렌더링]
    ↓
[브라우저에 HTML 응답]

3. 사용자가 보호된 페이지 접속 시도 (미인증 상태)
[브라우저] http://localhost:8080/dashboard 요청
    ↓
[Spring Security Filter Chain 진입]
    ↓
[SecurityFilterChain의 authorizeHttpRequests 확인]
    ├─→ "/dashboard"는 permitAll()에 해당 안됨
    ├─→ anyRequest().authenticated() 규칙 적용
    └─→ 인증 필요! 현재 미인증 상태
    ↓
[AuthenticationEntryPoint 동작]
    ├─→ 현재 요청 URL (/dashboard) 저장 (나중에 리다이렉트용)
    └─→ 로그인 페이지로 리다이렉트: /login
    ↓
[브라우저] /login으로 리다이렉트
    ↓
[AuthController.login() 실행]
    └─→ return "login"
    ↓
[templates/login.html 렌더링]
    ↓
[로그인 폼 표시]

```

## 4. 로그인 과정 (가장 중요!)
```
4-1. 사용자가 로그인 폼 제출
[브라우저] 로그인 폼 제출
    ├─→ username: "user"
    └─→ password: "1234" (평문)
    ↓
[POST /login 요청] (Spring Security가 자동 처리)
    ↓
[UsernamePasswordAuthenticationFilter 동작]
    ├─→ username과 password 추출
    └─→ UsernamePasswordAuthenticationToken 생성 (미인증 상태)
4-2. 인증 매니저 동작
[AuthenticationManager에게 인증 요청]
    ↓
[ProviderManager가 적절한 AuthenticationProvider 선택]
    ↓
[DaoAuthenticationProvider 선택됨]
    ├─→ UserDetailsService 구현체 필요
    └─→ CustomUserDetailService 호출
4-3. 사용자 정보 로드
[CustomUserDetailService.loadUserByUsername("user") 호출]
    ↓
    public UserDetails loadUserByUsername(String username) {
        ↓
        [userRepository.findByUsername("user") 실행]
            ↓
        [JPA가 SQL 생성 및 실행]
            SELECT * FROM users WHERE username = 'user'
            ↓
        [DB 조회 결과]
            ├─→ id: 1
            ├─→ username: "user"
            ├─→ password: "$2a$10$..." (BCrypt 해시값)
            └─→ role: "ROLE_USER"
            ↓
        [User 엔티티 객체 생성]
            ↓
        [Optional<User> 반환]
            ↓
        [.orElseThrow() 체크]
            ├─→ 사용자 존재함 → 계속 진행
            └─→ 사용자 없으면 → UsernameNotFoundException 발생 → 로그인 실패
            ↓
        [new CustomUserDetails(user) 생성]
            ├─→ User 엔티티를 감싸는 래퍼 객체
            └─→ UserDetails 인터페이스 구현
            ↓
        [CustomUserDetails 반환]
    }
    ↓
[DaoAuthenticationProvider에게 CustomUserDetails 전달]
```

## 4-4. 비밀번호 검증
```
[DaoAuthenticationProvider가 비밀번호 검증]
    ↓
[PasswordEncoder.matches() 호출]
    ├─→ 입력한 비밀번호: "1234" (평문)
    ├─→ DB의 비밀번호: "$2a$10$..." (암호화)
    └─→ BCrypt 알고리즘으로 비교
    ↓
[passwordEncoder.matches("1234", "$2a$10$...")]
    ├─→ "1234"를 BCrypt로 해시화
    ├─→ DB의 해시값과 비교
    └─→ 일치 여부 확인
    ↓
[비밀번호 일치!]
4-5. 인증 성공 처리
[Authentication 객체 생성 (인증된 상태)]
    ├─→ Principal: CustomUserDetails
    ├─→ Credentials: password (보안상 이후 제거됨)
    └─→ Authorities: [ROLE_USER]
    ↓
[SecurityContext에 Authentication 저장]
    ├─→ SecurityContextHolder.getContext().setAuthentication(authentication)
    └─→ 이제 애플리케이션 전체에서 현재 사용자 정보 접근 가능
    ↓
[Session에 SecurityContext 저장]
    ├─→ JSESSIONID 쿠키 생성
    └─→ 서버 세션에 인증 정보 저장
    ↓
[AuthenticationSuccessHandler 동작]
    ├─→ defaultSuccessUrl("/dashboard", true) 확인
    └─→ /dashboard로 리다이렉트
    ↓
[브라우저] /dashboard로 리다이렉트 (JSESSIONID 쿠키 포함)

```

## 5. 인증 후 대시보드 접근
```
[브라우저] GET /dashboard 요청 (JSESSIONID 쿠키 포함)
    ↓
[Spring Security Filter Chain]
    ↓
[SecurityContextPersistenceFilter]
    ├─→ JSESSIONID로 세션 조회
    ├─→ 세션에서 SecurityContext 복원
    └─→ SecurityContextHolder에 설정
    ↓
[authorizeHttpRequests 확인]
    ├─→ "/dashboard"는 anyRequest().authenticated()
    ├─→ SecurityContext에 인증 정보 있음 → 통과 ✓
    ↓
[AuthController.dashboard() 실행]
    ↓
    public String dashboard(
        @AuthenticationPrincipal UserDetails userDetails,
        Model model
    ) {
        ↓
        [@AuthenticationPrincipal 처리]
            ├─→ SecurityContextHolder.getContext().getAuthentication().getPrincipal()
            └─→ CustomUserDetails 객체 주입
        ↓
        [userDetails != null 체크]
            └─→ true (로그인 되어 있음)
        ↓
        [Model에 데이터 추가]
            ├─→ model.addAttribute("username", "user")
            ├─→ model.addAttribute("authorities", [ROLE_USER])
            └─→ model.addAttribute("password", "$2a$10$...")
        ↓
        return "dashboard"
    }
    ↓
[ViewResolver가 templates/dashboard.html 찾기]
    ↓
[Thymeleaf 템플릿 엔진이 Model 데이터로 HTML 렌더링]
    ├─→ ${username} → "user" 치환
    └─→ ${authorities} → [ROLE_USER] 치환
    ↓
[브라우저에 HTML 응답]

```

## 6. 권한이 필요한 페이지 접근 (인가 과정)
```
6-1. USER가 /admin 페이지 접근 시도
[브라우저] GET /admin/something 요청 (USER 권한으로 로그인 상태)
    ↓
[Spring Security Filter Chain]
    ↓
[SecurityContext 복원]
    ├─→ 현재 사용자: "user"
    └─→ 권한: [ROLE_USER]
    ↓
[authorizeHttpRequests 확인]
    ├─→ requestMatchers("/admin/**").hasRole("ADMIN")
    └─→ "/admin/something"은 이 규칙에 해당
    ↓
[권한 검사]
    ├─→ 필요 권한: ROLE_ADMIN
    ├─→ 현재 권한: ROLE_USER
    └─→ 권한 부족! ✗
    ↓
[AccessDeniedException 발생]
    ↓
[ExceptionTranslationFilter가 처리]
    ├─→ exceptionHandling.accessDeniedPage("/access-denied")
    └─→ /access-denied로 리다이렉트
    ↓
[ErrorController.accessDenied() 실행]
    └─→ return "access-denied"
    ↓
[templates/access-denied.html 렌더링]
    ↓
[브라우저에 "접근 거부" 페이지 표시]

```
## 6-2. ADMIN이 /admin 페이지 접근
```
[브라우저] GET /admin/something 요청 (ADMIN 권한으로 로그인)
    ↓
[Spring Security Filter Chain]
    ↓
[SecurityContext 복원]
    ├─→ 현재 사용자: "admin"
    └─→ 권한: [ROLE_ADMIN]
    ↓
[authorizeHttpRequests 확인]
    ├─→ requestMatchers("/admin/**").hasRole("ADMIN")
    └─→ "/admin/something"은 이 규칙에 해당
    ↓
[권한 검사]
    ├─→ 필요 권한: ROLE_ADMIN
    ├─→ 현재 권한: ROLE_ADMIN
    └─→ 권한 일치! ✓
    ↓
[해당 컨트롤러로 요청 전달]
    └─→ 정상 처리

```

## 7. 로그아웃 과정
```
[브라우저] POST /logout 요청 (Spring Security가 자동 처리)
    ↓
[LogoutFilter 동작]
    ↓
[SecurityContextLogoutHandler 실행]
    ├─→ SecurityContext 초기화
    ├─→ SecurityContextHolder.clearContext()
    └─→ 세션 무효화: session.invalidate()
    ↓
[쿠키 삭제]
    └─→ JSESSIONID 쿠키 제거
    ↓
[LogoutSuccessHandler 동작]
    ├─→ logoutSuccessUrl("/login") 확인
    └─→ /login으로 리다이렉트
    ↓
[브라우저] 로그인 페이지로 이동

```

### 전체 흐름도 (요약)
```
[애플리케이션 시작]
    ↓
[초기 데이터 생성 (user, admin)]
    ↓
[사용자 접속] ──→ [공개 페이지] ──→ [바로 접근]
    ↓
[보호된 페이지 접근 시도]
    ↓
[미인증?] ──Yes──→ [로그인 페이지로 리다이렉트]
    ↓                       ↓
   No                [로그인 폼 제출]
    ↓                       ↓
[권한 확인]        [CustomUserDetailService.loadUserByUsername()]
    ↓                       ↓
[권한 있음?]       [DB에서 사용자 조회]
    ↓                       ↓
   Yes               [CustomUserDetails 생성]
    ↓                       ↓
[컨트롤러 실행]    [비밀번호 검증]
    ↓                       ↓
[View 렌더링]      [인증 성공 → SecurityContext 저장]
    ↓                       ↓
[브라우저 응답]    [대시보드로 리다이렉트]
                           ↓
                    [이후 모든 요청에 인증 정보 포함]

```
 ### 핵심 포인트 정리
1. Security Filter Chain은 모든 요청을 가로챕니다

컨트롤러 실행 전에 먼저 동작
URL 패턴별로 권한 확인

2. 인증 정보는 SecurityContext에 저장

SecurityContextHolder를 통해 전역 접근
세션에 영속화되어 여러 요청에서 유지

3. CustomUserDetailService는 로그인 시 한 번만 호출

이후 요청은 세션에서 인증 정보 복원

4. PasswordEncoder는 단방향 암호화

저장: encode()로 암호화
검증: matches()로 비교 (복호화 안함)

5. 권한 확인은 매 요청마다 수행

SecurityFilterChain의 규칙에 따라 자동 검증
