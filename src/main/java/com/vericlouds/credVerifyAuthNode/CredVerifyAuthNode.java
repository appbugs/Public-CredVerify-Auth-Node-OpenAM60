/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017 ForgeRock AS.
 */
package com.vericlouds.credVerifyAuthNode;

import com.google.inject.assistedinject.Assisted;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;
import javax.inject.Inject;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import com.vericlouds.credVerifyAuthNode.CredVerify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ResourceBundle;

/** 
 * A node that checks to see if zero-page login headers have specified username and shared key 
 * for this request. 
 */
@Node.Metadata(outcomeProvider  = AbstractDecisionNode.OutcomeProvider.class, configClass = CredVerifyAuthNode.Config.class)
public class CredVerifyAuthNode extends AbstractDecisionNode {

    private final Config config;
    private final CoreWrapper coreWrapper;
    private static final String BUNDLE = CredVerifyAuthNode.class.getName().replace(".", "/");
	private final Logger logger = LoggerFactory.getLogger("VeriClouds");    
    private CredVerify credVerify;
    String checkPolicy;
    String userIdType;

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 100)
        default String apiUrl() {
            return "https://api.vericlouds.com/index.php";
        }

        @Attribute(order = 200)
        default String appKey() {
            return "";
        }

        @Attribute(order = 300)
        default String appSecret() {
            return "";
        }

        @Attribute(order = 400)
        default CheckPolicy checkPolicy() {
            return CheckPolicy.enterprise;
        }

        @Attribute(order = 500)
        default UserIdType userIdType() {
            return UserIdType.not_used;
        }        
    }

    /**
     * Create the node.
     * @param config The service config.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public CredVerifyAuthNode(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
        this.config = config;
        this.coreWrapper = coreWrapper;

        String apiUrl = config.apiUrl();
        String apiKey = config.appKey();
        String apiSecret = config.appSecret();
        checkPolicy = config.checkPolicy().toString();
        userIdType = config.userIdType().toString();

        if (apiUrl == null || apiUrl.trim().length() == 0){
            apiUrl = "https://api.vericlouds.com/index.php";
        }
        logger.info("VeriClouds::init apiUrl = " + apiUrl);
        logger.info("VeriClouds::init apiKey = " + apiKey);

        if (checkPolicy == null || checkPolicy.isEmpty()){
            throw new NodeProcessException("VeriClouds::init checkPolicy is not defined");
        }

        if (userIdType == null || userIdType.isEmpty()){
            throw new NodeProcessException("VeriClouds::init userIdType is not defined");
        }

        logger.info("VeriClouds::init checkPolicy = " + checkPolicy);
        logger.info("VeriClouds::init userIdType = " + userIdType);

        try{
            credVerify = new CredVerify(apiUrl, apiKey, apiSecret);
        }
        catch(Exception e){
            throw new NodeProcessException(e);
        }
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        logger.info("VeriClouds::process starts");

        String userName = (String) context.sharedState.get(USERNAME).asString();
        String userPassword = (String) context.transientState.get(PASSWORD).asString();
        logger.info("userName = " + userName);

        ResourceBundle bundle = context.request.locales.getBundleInPreferredLocale(BUNDLE, getClass().getClassLoader());
        try {
            logger.info("VeriClouds::process calling VeriClouds API");
            if (credVerify == null){
                logger.error("VeriClouds API is not proper configured");
                // if this module is required in the Auth chain but not configured, we don't want to block admin from login
                return goTo(true).build();
            }

            Boolean leaked = false;
            if (checkPolicy.equals("enterprise")){
                leaked = credVerify.verify(userPassword);
            }
            else{
                leaked = credVerify.verify(userName, userPassword, userIdType);
            }
            
            if (leaked == true){
                logger.info("VeriClouds::process VeriClouds API: password leaked is true");
                logger.error(bundle.getString("credverify-leaked-password"));
                return goTo(false).build();
            }
            else{
                logger.info("VeriClouds::process VeriClouds API: password leaked is false");
                return goTo(true).build();
            }
        } catch (Exception e) {
            logger.error("VeriClouds::process error", e);
            return goTo(true).build();
        }
    }

    public enum CheckPolicy {
        enterprise,
        consumer
    }

    public enum UserIdType {
        not_used,
        auto_detect,
        username,
        email,
        hashed_email,
        phone_number
    }
}