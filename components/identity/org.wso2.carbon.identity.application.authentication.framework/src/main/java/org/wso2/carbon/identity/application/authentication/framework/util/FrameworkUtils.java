/*
 *Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *WSO2 Inc. licenses this file to you under the Apache License,
 *Version 2.0 (the "License"); you may not use this file except
 *in compliance with the License.
 *You may obtain a copy of the License at
 *
 *http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an
 *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *KIND, either express or implied.  See the License for the
 *specific language governing permissions and limitations
 *under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.identity.application.authentication.framework.ApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.cache.*;
import org.wso2.carbon.identity.application.authentication.framework.config.ConfigurationFacade;
import org.wso2.carbon.identity.application.authentication.framework.config.builder.FileBasedConfigurationBuilder;
import org.wso2.carbon.identity.application.authentication.framework.config.model.AuthenticatorConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.ExternalIdPConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.SequenceConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.StepConfig;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.context.SessionContext;
import org.wso2.carbon.identity.application.authentication.framework.handler.claims.ClaimHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.claims.impl.DefaultClaimHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.hrd.HomeRealmDiscoverer;
import org.wso2.carbon.identity.application.authentication.framework.handler.hrd.impl.DefaultHomeRealmDiscoverer;
import org.wso2.carbon.identity.application.authentication.framework.handler.provisioning.ProvisioningHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.provisioning.impl.DefaultProvisioningHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.AuthenticationRequestHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.LogoutRequestHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.RequestCoordinator;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.impl.DefaultAuthenticationRequestHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.impl.DefaultLogoutRequestHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.impl.DefaultRequestCoordinator;
import org.wso2.carbon.identity.application.authentication.framework.handler.roles.RoleHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.roles.impl.DefaultRoleHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.sequence.RequestPathBasedSequenceHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.sequence.StepBasedSequenceHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.sequence.impl.DefaultRequestPathBasedSequenceHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.sequence.impl.DefaultStepBasedSequenceHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.step.StepHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.step.impl.DefaultStepHandler;
import org.wso2.carbon.identity.application.authentication.framework.internal.FrameworkServiceComponent;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedIdPData;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticationFrameworkWrapper;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticationResult;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.ui.CarbonUIUtil;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticationRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;

public class FrameworkUtils {

    private static int maxInactiveInterval;

    private static Log log = LogFactory.getLog(FrameworkUtils.class);

	/**
	 * To add authentication request cache entry to cache, with timeout
	 * @param key cache entry key
	 * @param authReqEntry AuthenticationReqCache Entry.
	 * @param cacheTimeout Cache timeout
	 */
    public static void addAuthenticationRequestToCache(
            String key, AuthenticationRequestCacheEntry authReqEntry, int cacheTimeout) {

        AuthenticationRequestCacheKey cacheKey = new AuthenticationRequestCacheKey(key);
        AuthenticationRequestCache.getInstance(cacheTimeout).addToCache(cacheKey, authReqEntry);
    }

	/**
	 * To add authentication request cache entry to the cache without timout
	 * @param key cache entry key
	 * @param authReqEntry AuthenticationReqCache Entry.
	 */
    public static void addAuthenticationRequestToCache(
            String key, AuthenticationRequestCacheEntry authReqEntry) {

        AuthenticationRequestCacheKey cacheKey = new AuthenticationRequestCacheKey(key);
        AuthenticationRequestCache.getInstance().addToCache(cacheKey, authReqEntry);
    }

	/**
	 * To get authentication cache request from cache
	 * @param key Key of the cache entry
	 * @return
	 */
    public static AuthenticationRequestCacheEntry getAuthenticationReqFromCache(String key) {

        AuthenticationRequestCacheEntry authRequest = null;
        AuthenticationRequestCacheKey cacheKey = new AuthenticationRequestCacheKey(key);
        Object cacheEntryObj = AuthenticationRequestCache.getInstance(0).getValueFromCache(cacheKey);

        if (cacheEntryObj != null) {
            authRequest = (AuthenticationRequestCacheEntry) cacheEntryObj;
        }

        return authRequest;
    }

	/**
	 * removes authentication request from cache.
	 * @param key SessionDataKey
	 */
    public static void removeAuthenticationRequestFromCache(String key) {

        if (key != null) {
            AuthenticationRequestCacheKey cacheKey = new AuthenticationRequestCacheKey(key);
            AuthenticationRequestCache.getInstance(0).clearCacheEntry(cacheKey);
        }
    }

    /**
     * Builds the wrapper, wrapping incoming request and information take from cache entry
     *
     * @param request    Original request coming to authentication framework
     * @param cacheEntry Cache entry from the cache, which is added from calling servlets
     * @return
     */
    public static HttpServletRequest getCommonAuthReqWithParams(
            HttpServletRequest request, AuthenticationRequestCacheEntry cacheEntry) {

        Map<String, String[]> modifiableParameters = new TreeMap<String, String[]>();

        if (cacheEntry != null) {
            AuthenticationRequest authenticationRequest = cacheEntry.getAuthenticationRequest();
            // Adding field variables to wrapper
            if (authenticationRequest.getType() != null) {
                modifiableParameters.put(FrameworkConstants.RequestParams.TYPE,
                        new String[]{authenticationRequest.getType()});
            }
            if (authenticationRequest.getCommonAuthCallerPath() != null) {
                modifiableParameters.put(FrameworkConstants.RequestParams.CALLER_PATH,
                        new String[]{authenticationRequest.getCommonAuthCallerPath()});
            }
            if (authenticationRequest.getRelyingParty() != null) {
                modifiableParameters.put(FrameworkConstants.RequestParams.ISSUER,
                        new String[]{authenticationRequest.getRelyingParty()});
            }
            if (authenticationRequest.getTenantDomain() != null) {
                modifiableParameters.put(FrameworkConstants.RequestParams.TENANT_DOMAIN,
                        new String[]{authenticationRequest.getTenantDomain()});
            }
            modifiableParameters.put(FrameworkConstants.RequestParams.FORCE_AUTHENTICATE,
                    new String[]{String.valueOf(authenticationRequest.getForceAuth())});
            modifiableParameters.put(FrameworkConstants.RequestParams.PASSIVE_AUTHENTICATION,
                    new String[]{String.valueOf(authenticationRequest.getPassiveAuth())});

            if (!authenticationRequest.getRequestQueryParams().isEmpty()) {
                modifiableParameters.putAll(authenticationRequest.getRequestQueryParams());
            }

            if (log.isDebugEnabled()) {
                StringBuilder queryStringBuilder = new StringBuilder("");

                for (Map.Entry<String, String[]> entry : modifiableParameters.entrySet()) {
                    StringBuilder paramValueBuilder = new StringBuilder("");
                    String[] paramValueArr = entry.getValue();

                    if (paramValueArr != null) {
                        for (String paramValue : paramValueArr) {
                            paramValueBuilder.append("{").append(paramValue).append("}");
                        }
                    }

                    queryStringBuilder.append("\n").append(
                            entry.getKey() + "=" + paramValueBuilder.toString());
                }

                log.debug("\nInbound Request parameters: " + queryStringBuilder.toString());
            }

            return new AuthenticationFrameworkWrapper(request, modifiableParameters,
                    authenticationRequest.getRequestHeaders());
        }
        return request;
    }

    /**
     * @param name
     * @return
     */
    public static ApplicationAuthenticator getAppAuthenticatorByName(String name) {

        for (ApplicationAuthenticator authenticator : FrameworkServiceComponent.authenticators) {

            if (name.equals(authenticator.getName())) {
                return authenticator;
            }
        }

        return null;
    }

    /**
     * @param request
     * @return
     */
    public static AuthenticationContext getContextData(HttpServletRequest request) {

        AuthenticationContext context = null;

        for (ApplicationAuthenticator authenticator : FrameworkServiceComponent.authenticators) {
            try {
                String contextIdentifier = authenticator.getContextIdentifier(request);

                if (contextIdentifier != null && !contextIdentifier.isEmpty()) {
                    context = FrameworkUtils.getAuthenticationContextFromCache(contextIdentifier);
                    if (context != null) {
                        break;
                    }
                }
            } catch (UnsupportedOperationException e) {
                continue;
            }
        }

        return context;
    }

    public static RequestCoordinator getRequestCoordinator() {

        RequestCoordinator requestCoordinator = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_REQ_COORDINATOR);

        if (obj instanceof RequestCoordinator) {
            requestCoordinator = (RequestCoordinator) obj;
        } else {
            requestCoordinator = DefaultRequestCoordinator.getInstance();
        }

        return requestCoordinator;
    }

    /**
     * @return
     */
    public static AuthenticationRequestHandler getAuthenticationRequestHandler() {

        AuthenticationRequestHandler authenticationRequestHandler = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_AUTH_REQ_HANDLER);

        if (obj instanceof AuthenticationRequestHandler) {
            authenticationRequestHandler = (AuthenticationRequestHandler) obj;
        } else {
            authenticationRequestHandler = DefaultAuthenticationRequestHandler.getInstance();
        }

        return authenticationRequestHandler;
    }

    /**
     * @return
     */
    public static LogoutRequestHandler getLogoutRequestHandler() {

        LogoutRequestHandler logoutRequestHandler = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_LOGOUT_REQ_HANDLER);

        if (obj instanceof AuthenticationRequestHandler) {
            logoutRequestHandler = (LogoutRequestHandler) obj;
        } else {
            logoutRequestHandler = DefaultLogoutRequestHandler.getInstance();
        }

        return logoutRequestHandler;
    }

    /**
     * @return
     */
    public static StepBasedSequenceHandler getStepBasedSequenceHandler() {

        StepBasedSequenceHandler stepBasedSequenceHandler = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_STEP_BASED_SEQ_HANDLER);

        if (obj instanceof StepBasedSequenceHandler) {
            stepBasedSequenceHandler = (StepBasedSequenceHandler) obj;
        } else {
            stepBasedSequenceHandler = DefaultStepBasedSequenceHandler.getInstance();
        }

        return stepBasedSequenceHandler;
    }

    /**
     * @return
     */
    public static RequestPathBasedSequenceHandler getRequestPathBasedSequenceHandler() {

        RequestPathBasedSequenceHandler reqPathBasedSeqHandler = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_REQ_PATH_BASED_SEQ_HANDLER);

        if (obj instanceof RequestPathBasedSequenceHandler) {
            reqPathBasedSeqHandler = (RequestPathBasedSequenceHandler) obj;
        } else {
            reqPathBasedSeqHandler = DefaultRequestPathBasedSequenceHandler.getInstance();
        }

        return reqPathBasedSeqHandler;
    }

    /**
     * @return
     */
    public static StepHandler getStepHandler() {

        StepHandler stepHandler = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_STEP_HANDLER);

        if (obj instanceof StepHandler) {
            stepHandler = (StepHandler) obj;
        } else {
            stepHandler = DefaultStepHandler.getInstance();
        }

        return stepHandler;
    }

    /**
     * @return
     */
    public static HomeRealmDiscoverer getHomeRealmDiscoverer() {

        HomeRealmDiscoverer homeRealmDiscoverer = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_HRD);

        if (obj instanceof HomeRealmDiscoverer) {
            homeRealmDiscoverer = (HomeRealmDiscoverer) obj;
        } else {
            homeRealmDiscoverer = DefaultHomeRealmDiscoverer.getInstance();
        }

        return homeRealmDiscoverer;
    }

    /**
     * @return
     */
    public static ClaimHandler getClaimHandler() {

        ClaimHandler claimHandler = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_CLAIM_HANDLER);

        if (obj instanceof ClaimHandler) {
            claimHandler = (ClaimHandler) obj;
        } else {
            claimHandler = DefaultClaimHandler.getInstance();
        }

        return claimHandler;
    }

    /**
     * @return
     */
    public static RoleHandler getRoleHandler() {

        RoleHandler roleHandler = null;

        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_ROLE_HANDLER);

        if (obj instanceof RoleHandler) {
            roleHandler = (RoleHandler) obj;
        } else {
            roleHandler = DefaultRoleHandler.getInstance();
        }

        return roleHandler;
    }

    /**
     * @return
     */
    public static ProvisioningHandler getProvisioningHandler() {

        ProvisioningHandler provisioningHandler = null;
        Object obj = ConfigurationFacade.getInstance().getExtensions()
                .get(FrameworkConstants.Config.QNAME_EXT_PROVISIONING_HANDLER);

        if (obj instanceof ProvisioningHandler) {
            provisioningHandler = (ProvisioningHandler) obj;
        } else {
            provisioningHandler = DefaultProvisioningHandler.getInstance();
        }

        return provisioningHandler;
    }

    /**
     * @param request
     * @param response
     * @throws IOException
     */
    public static void sendToRetryPage(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // TODO read the URL from framework config file rather than carbon.xml
        String redirectURL = CarbonUIUtil.getAdminConsoleURL(request);
        redirectURL = redirectURL.replace("commonauth/carbon/", "authenticationendpoint/retry.do");
        response.sendRedirect(redirectURL);
    }

    /**
     * @param req
     * @param resp
     */
    public static void removeAuthCookie(HttpServletRequest req, HttpServletResponse resp) {

        Cookie[] cookies = req.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(FrameworkConstants.COMMONAUTH_COOKIE)) {
                    cookie.setMaxAge(0);
                    resp.addCookie(cookie);
                    break;
                }
            }
        }
    }

    /**
     * @param req
     * @param resp
     * @param id
     */
    public static void storeAuthCookie(HttpServletRequest req, HttpServletResponse resp, String id) {
        storeAuthCookie(req, resp, id, null);
    }

    /**
     * @param req
     * @param resp
     * @param id
     * @param age
     */
    public static void storeAuthCookie(HttpServletRequest req, HttpServletResponse resp, String id, Integer age) {

        Cookie authCookie = new Cookie(FrameworkConstants.COMMONAUTH_COOKIE, id);
        authCookie.setSecure(true);
        authCookie.setHttpOnly(true);

        if (age != null) {
            authCookie.setMaxAge(age.intValue() * 60);
        }

        resp.addCookie(authCookie);
    }

    /**
     * @param req
     * @return
     */
    public static Cookie getAuthCookie(HttpServletRequest req) {

        Cookie[] cookies = req.getCookies();

        if (cookies != null) {

            for (Cookie cookie : cookies) {

                if (cookie.getName().equals(FrameworkConstants.COMMONAUTH_COOKIE)) {
                    return cookie;
                }
            }
        }

        return null;
    }

    /**
     * @param key
     * @param context
     * @param cacheTimeout
     */
    public static void addAuthenticationContextToCache(String key, AuthenticationContext context,
                                                       int cacheTimeout) {

        AuthenticationContextCacheKey cacheKey = new AuthenticationContextCacheKey(key);
        AuthenticationContextCacheEntry cacheEntry = new AuthenticationContextCacheEntry();
        cacheEntry.setContext(context);
        AuthenticationContextCache.getInstance(cacheTimeout).addToCache(cacheKey, cacheEntry);
    }

    /**
     * @param key
     * @param authenticationResult
     * @param cacheTimeout
     */
    public static void addAuthenticationResultToCache(String key,
                                                      AuthenticationResult authenticationResult, int cacheTimeout) {

        AuthenticationResultCacheKey cacheKey = new AuthenticationResultCacheKey(key);
        AuthenticationResultCacheEntry cacheEntry = new AuthenticationResultCacheEntry();
        cacheEntry.setResult(authenticationResult);
        AuthenticationResultCache.getInstance(cacheTimeout).addToCache(cacheKey, cacheEntry);
    }



    /**
     * @param key
     * @param sessionContext
     * @param cacheTimeout
     */
    public static void addSessionContextToCache(String key, SessionContext sessionContext, int cacheTimeout)
    {
        SessionContextCacheKey cacheKey = new SessionContextCacheKey(key);
        SessionContextCacheEntry cacheEntry = new SessionContextCacheEntry();

        Map<String, SequenceConfig> seqData = sessionContext.getAuthenticatedSequences();
        if (seqData != null) {
            for (Entry<String, SequenceConfig> entry : seqData.entrySet()) {
                if (entry.getValue() != null) {
                    ((SequenceConfig)entry.getValue()).setUserAttributes(null);
                }
            }
        }

        cacheEntry.setContext(sessionContext);
        SessionContextCache.getInstance(cacheTimeout).addToCache(cacheKey, cacheEntry);
    }

    /**
     * @param key
     * @return
     */
    public static SessionContext getSessionContextFromCache(String key) {

        SessionContext sessionContext = null;
        SessionContextCacheKey cacheKey = new SessionContextCacheKey(key);
        Object cacheEntryObj = SessionContextCache.getInstance(0).getValueFromCache(cacheKey);

        if (cacheEntryObj != null) {
            sessionContext = ((SessionContextCacheEntry) cacheEntryObj).getContext();
        }

        return sessionContext;
    }

    /**
     * @param key
     */
    public static void removeSessionContextFromCache(String key) {

        if (key != null) {
            SessionContextCacheKey cacheKey = new SessionContextCacheKey(key);
            SessionContextCache.getInstance(0).clearCacheEntry(cacheKey);
        }
    }

    /**
     * @param key
     */
    public static void removeAuthenticationContextFromCache(String key) {

        if (key != null) {
            AuthenticationContextCacheKey cacheKey = new AuthenticationContextCacheKey(key);
            AuthenticationContextCache.getInstance(0).clearCacheEntry(cacheKey);
        }
    }

    /**
     * @param contextId
     * @return
     */
    public static AuthenticationContext getAuthenticationContextFromCache(String contextId) {

        AuthenticationContext authnContext = null;
        AuthenticationContextCacheKey cacheKey = new AuthenticationContextCacheKey(contextId);
        Object cacheEntryObj = AuthenticationContextCache.getInstance(0)
                .getValueFromCache(cacheKey);

        if (cacheEntryObj != null) {
            authnContext = ((AuthenticationContextCacheEntry) cacheEntryObj).getContext();
        }

        if(log.isDebugEnabled()){
            if(authnContext == null){
                log.debug("Authentication Context is null");
            }
        }

        return authnContext;
    }

    /**
     * @param req
     */
    public static void setRequestPathCredentials(HttpServletRequest req) {
        // reading the authorization header for request path authentication
        String reqPathCred = req.getHeader("Authorization");
        if (reqPathCred == null) {
            reqPathCred = req.getParameter("ReqPathCredential");
        }
        if (reqPathCred != null) {
            log.debug("A Request path credential found");
            req.getSession().setAttribute("Authorization", reqPathCred);
        }
    }

    /**
     * @param externalIdPConfig
     * @param name
     * @return
     */
    public static Map<String, String> getAuthenticatorPropertyMapFromIdP(
            ExternalIdPConfig externalIdPConfig, String name) {

        Map<String, String> propertyMap = new HashMap<String, String>();

        if (externalIdPConfig != null) {
            FederatedAuthenticatorConfig[] authenticatorConfigs = externalIdPConfig
                    .getIdentityProvider().getFederatedAuthenticatorConfigs();

            for (FederatedAuthenticatorConfig authenticatorConfig : authenticatorConfigs) {

                if (authenticatorConfig.getName().equals(name)) {

                    for (Property property : authenticatorConfig.getProperties()) {
                        propertyMap.put(property.getName(), property.getValue());
                    }
                    break;
                }
            }
        }

        return propertyMap;
    }

    /**
     * @param attributeValue
     * @return
     */
    public static Map<ClaimMapping, String> buildClaimMappings(Map<String, String> attributeValue) {

        Map<ClaimMapping, String> claimMap = new HashMap<ClaimMapping, String>();

        for (Iterator<Entry<String, String>> iterator = attributeValue.entrySet().iterator(); iterator
                .hasNext(); ) {
            Entry<String, String> entry = iterator.next();
            if(entry.getValue() == null){
            	continue;
            }
            claimMap.put(ClaimMapping.build(entry.getKey(), entry.getKey(), null, false),
                    entry.getValue());
        }

        return claimMap;

    }

    /**
     * @param attributeValues
     * @return
     */
    public static Set<String> getKeySet(Map<ClaimMapping, String> attributeValues) {

        Set<String> claimList = new HashSet<String>();

        for (Iterator<Entry<ClaimMapping, String>> iterator = attributeValues.entrySet().iterator(); iterator
                .hasNext(); ) {
            Entry<ClaimMapping, String> entry = iterator.next();
            claimList.add(entry.getKey().getLocalClaim().getClaimUri());

        }

        return claimList;

    }

    /**
     * @param remoteIdpClaimMapping
     * @return
     */
    public static Map<String, String> getClaimMappings(ClaimMapping[] remoteIdpClaimMapping,
                                                       boolean useRemoteAsClaimKey) {

        Map<String, String> idpToLocalClaimMap = new HashMap<String, String>();

        for (ClaimMapping claimMapping : remoteIdpClaimMapping) {
            if (useRemoteAsClaimKey) {
                idpToLocalClaimMap.put(claimMapping.getRemoteClaim().getClaimUri(), claimMapping
                        .getLocalClaim().getClaimUri());
            } else {
                idpToLocalClaimMap.put(claimMapping.getLocalClaim().getClaimUri(), claimMapping
                        .getRemoteClaim().getClaimUri());
            }
        }
        return idpToLocalClaimMap;
    }

    /**
     * @param remoteIdpClaimMapping
     * @param useRemoteAsClaimKey
     * @return
     */
    public static Map<String, String> getClaimMappings(
            Map<ClaimMapping, String> remoteIdpClaimMapping, boolean useRemoteAsClaimKey) {

        Map<String, String> idpToLocalClaimMap = new HashMap<String, String>();

        for (Entry<ClaimMapping, String> entry : remoteIdpClaimMapping.entrySet()) {
            ClaimMapping claimMapping = entry.getKey();
            if (useRemoteAsClaimKey) {
                idpToLocalClaimMap.put(claimMapping.getRemoteClaim().getClaimUri(),
                        entry.getValue());
            } else {
                idpToLocalClaimMap
                        .put(claimMapping.getLocalClaim().getClaimUri(), entry.getValue());
            }
        }
        return idpToLocalClaimMap;
    }

    public static String getQueryStringWithFrameworkContextId(String originalQueryStr,
                                                              String callerContextId, String frameworkContextId) {

        String queryParams = originalQueryStr;

        /*
         * Upto now, query-string contained a 'sessionDataKey' of the calling servlet. At here we
         * replace it with the framework context id.
         */
        queryParams = queryParams.replace(callerContextId, frameworkContextId);

        return queryParams;
    }

    public static List<String> getStepIdPs(StepConfig stepConfig) {

        List<String> stepIdps = new ArrayList<String>();
        List<AuthenticatorConfig> authenticatorConfigs = stepConfig.getAuthenticatorList();

        for (AuthenticatorConfig authenticatorConfig : authenticatorConfigs) {
            List<String> authenticatorIdps = authenticatorConfig.getIdpNames();

            for (String authenticatorIdp : authenticatorIdps) {
                stepIdps.add(authenticatorIdp);
            }
        }

        return stepIdps;
    }

    public static List<String> getAuthenticatedStepIdPs(List<String> stepIdPs,
                                                        List<String> authenticatedIdPs) {

        List<String> idps = new ArrayList<String>();

        if (stepIdPs != null && authenticatedIdPs != null) {
            for (String stepIdP : stepIdPs) {
                if (authenticatedIdPs.contains(stepIdP)) {
                    idps.add(stepIdP);
                    break;
                }
            }
        }

        return idps;
    }

    public static Map<String, AuthenticatorConfig> getAuthenticatedStepIdPs(StepConfig stepConfig,
                                                                            Map<String, AuthenticatedIdPData> authenticatedIdPs) {

        if (log.isDebugEnabled()) {
            log.debug("Finding already authenticated IdPs of the Step");
        }

        Map<String, AuthenticatorConfig> idpAuthenticatorMap = new HashMap<String, AuthenticatorConfig>();
        List<AuthenticatorConfig> authenticatorConfigs = stepConfig.getAuthenticatorList();

        if (authenticatedIdPs != null && !authenticatedIdPs.isEmpty()) {

            for (AuthenticatorConfig authenticatorConfig : authenticatorConfigs) {
                List<String> authenticatorIdps = authenticatorConfig.getIdpNames();

                for (String authenticatorIdp : authenticatorIdps) {
                    AuthenticatedIdPData authenticatedIdPData = authenticatedIdPs
                            .get(authenticatorIdp);

                    if (authenticatedIdPData != null
                            && authenticatedIdPData.getIdpName().equals(authenticatorIdp)) {
                        idpAuthenticatorMap.put(authenticatorIdp, authenticatorConfig);
                        break;
                    }
                }
            }
        }

        return idpAuthenticatorMap;
    }

    public static String getAuthenticatorIdPMappingString(List<AuthenticatorConfig> authConfigList) {

        StringBuilder authenticatorIdPStr = new StringBuilder("");

        for (AuthenticatorConfig authConfig : authConfigList) {
            StringBuilder idpsOfAuthenticatorStr = new StringBuilder("");

            for (String idpName : authConfig.getIdpNames()) {

                if (idpName != null) {

                    if (idpsOfAuthenticatorStr.length() != 0) {
                        idpsOfAuthenticatorStr.append(":");
                    }

                    IdentityProvider idp = authConfig.getIdps().get(idpName);

                    if (idp.isFederationHub()) {
                        idpName += ".hub";
                    }

                    idpsOfAuthenticatorStr.append(idpName);
                }
            }

            if (authenticatorIdPStr.length() != 0) {
                authenticatorIdPStr.append(";");
            }

            authenticatorIdPStr.append(authConfig.getName()).append(":")
                    .append(idpsOfAuthenticatorStr);
        }

        return authenticatorIdPStr.toString();
    }

	/**
	 * when getting query params through this, only configured params will be appended as query params
	 * The required params can be configured from application-authenticators.xml
	 * @param request
	 * @return
	 */
    public static String getQueryStringWithConfiguredParams(HttpServletRequest request) {

        boolean configAvailable = FileBasedConfigurationBuilder.getInstance().isAuthEndpointQueryParamsConfigAvailable();
        List<String> queryParams = FileBasedConfigurationBuilder.getInstance()
                .getAuthEndpointQueryParams();
        String action = FileBasedConfigurationBuilder.getInstance()
                .getAuthEndpointQueryParamsAction();

        StringBuilder queryStrBuilder = new StringBuilder("");
        Map<String, String[]> reqParamMap = request.getParameterMap();

        if (configAvailable) {
            if (action != null
                    && action.equals(FrameworkConstants.AUTH_ENDPOINT_QUERY_PARAMS_ACTION_EXCLUDE)) {
                if (reqParamMap != null) {
                    for (Map.Entry<String, String[]> entry : reqParamMap.entrySet()) {
                        String paramName = entry.getKey();
                        String paramValue = entry.getValue()[0];

                        //skip the sessionDataKey sent from the servlet.
                        if (paramName.equals("sessionDataKey")) {
                            continue;
                        }

                        if (!queryParams.contains(paramName)) {
                            if (queryStrBuilder.length() > 0) {
                                queryStrBuilder.append('&');
                            }

                            try {
                                queryStrBuilder.append(URLEncoder.encode(paramName, "UTF-8")).append('=')
                                        .append(URLEncoder.encode(paramValue, "UTF-8"));
                            } catch (UnsupportedEncodingException e) {
                                log.error(
                                        "Error while URL Encoding query param to be sent to the AuthenticationEndpoint",
                                        e);
                            }
                        }
                    }
                }
            } else {
                if (reqParamMap != null) {
                    for (Map.Entry<String, String[]> entry : reqParamMap.entrySet()) {
                        String paramName = entry.getKey();
                        String paramValue = entry.getValue()[0];

                        //skip the sessionDataKey sent from the servlet.
                        if (paramName.equals("sessionDataKey")) {
                            continue;
                        }

                        if (queryStrBuilder.length() > 0) {
                            queryStrBuilder.append('&');
                        }

                        try {
                            queryStrBuilder.append(URLEncoder.encode(paramName, "UTF-8")).append('=')
                                    .append(URLEncoder.encode(paramValue, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            log.error(
                                    "Error while URL Encoding query param to be sent to the AuthenticationEndpoint",
                                    e);
                        }
                    }
                }
            }
        } else {
            for (String param : queryParams) {
                String paramValue = request.getParameter(param);

                if (paramValue != null) {
                    if (queryStrBuilder.length() > 0) {
                        queryStrBuilder.append('&');
                    }
                    try {
                        queryStrBuilder.append(URLEncoder.encode(param, "UTF-8")).append('=')
                                .append(URLEncoder.encode(paramValue, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        log.error(
                                "Error while URL Encoding query param to be sent to the AuthenticationEndpoint",
                                e);
                    }
                }
            }
        }
        return queryStrBuilder.toString();
    }

    public static int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public static void setMaxInactiveInterval(int maxInactiveInterval) {
        FrameworkUtils.maxInactiveInterval = maxInactiveInterval;
    }

    public static String prependUserStoreDomainToName(String authenticatedSubject) {

        if (authenticatedSubject == null || authenticatedSubject.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid argument. authenticatedSubject : "
                  +  authenticatedSubject);
        }
        if (authenticatedSubject.indexOf(CarbonConstants.DOMAIN_SEPARATOR) < 0) {
            if (UserCoreUtil.getDomainFromThreadLocal() != null
                    && !UserCoreUtil.getDomainFromThreadLocal().isEmpty()) {
                authenticatedSubject = UserCoreUtil.getDomainFromThreadLocal()
                + CarbonConstants.DOMAIN_SEPARATOR + authenticatedSubject;
            }
        } else if (authenticatedSubject.indexOf(CarbonConstants.DOMAIN_SEPARATOR) == 0) {
            throw new IllegalArgumentException("Invalid argument. authenticatedSubject : "
                   + authenticatedSubject + " begins with \'" + CarbonConstants.DOMAIN_SEPARATOR
                   + "\'");
        }
        return authenticatedSubject;
    }
}
