/**
 * Copyright (c) 2015-2020, Michael Yang 杨福海 (fuhai999@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jboot.support.jwt;

import com.jfinal.kit.JsonKit;
import com.jfinal.log.Log;
import io.jboot.Jboot;
import io.jboot.exception.JbootException;
import io.jboot.utils.StrUtil;
import io.jboot.web.controller.JbootControllerContext;
import io.jsonwebtoken.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Michael Yang 杨福海 （fuhai999@gmail.com）
 * @version V1.0
 */
public class JwtManager {

    private static final JwtManager me = new JwtManager();
    private static final Log LOG = Log.getLog(JwtManager.class);

    public static JwtManager me() {
        return me;
    }

    private ThreadLocal<Map> jwtThreadLocal = ThreadLocal.withInitial(() -> {
        if (!getConfig().isConfigOk()) {
            throw new JbootException("Jwt secret can not config well, please config jboot.web.jwt.secret in jboot.properties.");
        }

        HttpServletRequest request = JbootControllerContext.get().getRequest();
        String token = request.getHeader(JwtManager.me().getHttpHeaderName());

        if (StrUtil.isBlank(token) && StrUtil.isNotBlank(JwtManager.me().getHttpParameterKey())) {
            token = request.getParameter(JwtManager.me().getHttpParameterKey());
        }

        if (StrUtil.isBlank(token)){
            return null;
        }

        return parseJwtToken(token);
    });


    public <T> T getPara(String key) {
        Map map = jwtThreadLocal.get();
        return map == null ? null : (T) map.get(key);
    }

    public Map getParas() {
        return jwtThreadLocal.get();
    }

    public String getHttpHeaderName() {
        return getConfig().getHttpHeaderName();
    }

    public String getHttpParameterKey() {
        return getConfig().getHttpParameterKey();
    }

    public Map parseJwtToken(String token) {
        SecretKey secretKey = generalKey();
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(token).getBody();

            String jsonString = claims.getSubject();
            if (StrUtil.isNotBlank(jsonString)) {
                return JsonKit.parse(jsonString, HashMap.class);
            }
        } catch (SignatureException | MalformedJwtException e) {
            // don't trust the JWT!
            // jwt 签名错误或解析错误，可能是伪造的，不能相信
            LOG.error("do not trast the jwt!",e);
        } catch (ExpiredJwtException e) {
            // jwt 已经过期
            LOG.error("jwt is expired!",e);
        } catch (Throwable ex) {
            //其他错误
            LOG.error("jwt parseJwtToken error, return null.");
        }

        return null;
    }

    public String createJwtToken(Map map) {

        if (!getConfig().isConfigOk()) {
            throw new JbootException("can not create jwt, please config jboot.web.jwt.secret in jboot.properties.");
        }

        SecretKey secretKey = generalKey();

        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        map.put(JwtInterceptor.ISUUED_AT, nowMillis);
        String subject = JsonKit.toJson(map);

        JwtBuilder builder = Jwts.builder()
                .setIssuedAt(now)
                .setSubject(subject)
                .signWith(signatureAlgorithm, secretKey);

        if (getConfig().getValidityPeriod() > 0) {
            long expMillis = nowMillis + getConfig().getValidityPeriod();
            builder.setExpiration(new Date(expMillis));
        }

        return builder.compact();
    }


    private SecretKey generalKey() {
        byte[] encodedKey = DatatypeConverter.parseBase64Binary(getConfig().getSecret());
        SecretKey key = new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");
        return key;
    }

    private JwtConfig config;

    public JwtConfig getConfig() {
        if (config == null) {
            config = Jboot.config(JwtConfig.class);
        }
        return config;
    }


}
