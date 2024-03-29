package com.uploadservice.config.security;

import com.uploadservice.common.enums.MainURIEnum;
import com.uploadservice.video.entity.UserEntity;
import com.uploadservice.video.service.CustomUserDetailService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Log4j2
@Configuration
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private CustomUserDetailService userDetailService;
    private JwtComponent jwtComponent;
    private CookieComponent cookieComponent;

    @Autowired
    public void setCustomUserDetailService(CustomUserDetailService _userDetailService) {
        this.userDetailService = _userDetailService;
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
    public void onAuthenticationSuccess(
            HttpServletRequest _req
            , HttpServletResponse _res
            , Authentication _auth
    ) throws IOException {
        Object pricipalObj = _auth.getPrincipal();
        String userEmail = ((User) pricipalObj).getUsername();
        UserEntity user = userDetailService.loadUserInfoByEmail(userEmail);
        String token = jwtComponent.generateToken(user.getVu_email());
        String refreshJwt = jwtComponent.generateRefreshToken(user);
        Cookie accessToken = cookieComponent.createCookie(JwtComponent.ACCESS_TOKEN_NAME, token);
        Cookie refreshToken = cookieComponent.createCookie(JwtComponent.REFRESH_TOKEN_NAME, refreshJwt);
        String redirectUri = (user.getVu_level().equals("A") ? MainURIEnum.ADMIN_MAIN_URL_REDIRECT.getMessage() : MainURIEnum.MEMBER_MAIN_URL_REDIRECT.getMessage());
        //실서비스의 경우 redis를 통해 토큰관리
//            redisComponent.setDataExpire(refreshJwt, user.getVu_email(), JwtComponent.REFRESH_TOKEN_VALIDATION_SECOND);
        userDetailService.patchTokenInfo(refreshJwt, user.getVu_email(), JwtComponent.REFRESH_TOKEN_VALIDATION_SECOND);
        _res.addCookie(accessToken);
        _res.addCookie(refreshToken);
        _res.sendRedirect(redirectUri);   //실 서비스라면 DB를 통해 사용자의 설정된 메인메뉴로 이동
    }
}
