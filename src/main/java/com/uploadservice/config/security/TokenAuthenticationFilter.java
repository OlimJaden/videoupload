package com.uploadservice.config.security;

import com.uploadservice.video.entity.UserEntity;
import com.uploadservice.video.service.CustomUserDetailService;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Log4j2
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private CustomUserDetailService userDetailsService;
    private JwtComponent jwtComponent;
    private CookieComponent cookieComponent;

    @Autowired
    public void setCustomUserDetailService(CustomUserDetailService _userDetailsService) {
        this.userDetailsService = _userDetailsService;
    }
    @Autowired
    public void setJwtComponent(JwtComponent _jwtComponent) {
        this.jwtComponent = _jwtComponent;
    }
    @Autowired
    public void setCookieComponent(CookieComponent _cookieComponent) {
        this.cookieComponent = _cookieComponent;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest _req
            , HttpServletResponse _res
            , FilterChain _filterChain
    ) throws ServletException, IOException {
        String[] publicUri = SecurityConfiguration.PUBLIC_URI;
        boolean jwtCheckTf = true;
        for(String uri : publicUri) {
            uri = uri.replace("**","");
            if(_req.getRequestURI().equals(uri) || _req.getRequestURI().contains(uri))
                jwtCheckTf=false;
        }

        if(jwtCheckTf)
            jwtAuthentication(_req, _res);
        _filterChain.doFilter(_req, _res);
    }

    /**
     * 사용자의 발급된 토큰의 유효성을 확인한다. ( 실서비스 에선 redis를 구축하여 토큰을 관리해야한다 )
     * @param _req
     * @return boolean
     */
    private void jwtAuthentication(HttpServletRequest _req, HttpServletResponse _res) throws IOException {
        String email = null;
        String jwt = null;
        String refreshJwt = null;
        String refreshEmail = null;
        final Cookie jwtToken = cookieComponent.getCookie(_req, JwtComponent.ACCESS_TOKEN_NAME);

        //access token 검증
        try {
            if(jwtToken != null){
                jwt = jwtToken.getValue();
                email = jwtComponent.getUsername(jwt);
            }
            if(email!=null){
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                if(jwtComponent.validateToken(jwt, userDetails)){
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(userDetails,null,userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(_req));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    return;
                }
            }
        } catch (ExpiredJwtException _e){
            Cookie refreshToken = cookieComponent.getCookie(_req, JwtComponent.REFRESH_TOKEN_NAME);
            if(refreshToken!=null){
                refreshJwt = refreshToken.getValue();
            }
        }

        //refresh token검증
        try {
            if(refreshJwt != null){
                //실서비스 에선 redis를 구축하여 토큰을 관리해야한다.
//                refreshUname = redisComponent.getData(refreshJwt);
                UserEntity userEntity = userDetailsService.loadUserinfoInfoByToken(refreshJwt);
                refreshEmail = userEntity.getVu_email();

                if(refreshEmail.equals(jwtComponent.getUsername(refreshJwt))){
                    UserDetails userDetails = userDetailsService.loadUserByUsername(refreshEmail);
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(userDetails,null,userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(_req));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    String newToken =jwtComponent.generateToken(refreshEmail);
                    Cookie newAccessToken  = cookieComponent.createCookie(JwtComponent.ACCESS_TOKEN_NAME,newToken);
                    _res.addCookie(newAccessToken);
                }
            } else {
                log.error("not found token info, retry login");
                _res.sendRedirect("/page/login-view");
                return;
            }
        } catch(ExpiredJwtException | IOException _e) {
            log.error(_e);
            _res.sendRedirect("/page/login-view");
        }
    }
}