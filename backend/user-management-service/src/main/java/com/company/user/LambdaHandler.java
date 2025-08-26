package com.company.user;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(UserManagementServiceApplication.class);
            // Enable request and response logging
            handler.getContainerConfig().setStripBasePath(true);
        } catch (ContainerInitializationException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not initialize Spring Boot application for Lambda", e);
        }
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest input, Context context) {
        // Log the request for debugging
        context.getLogger().log("Processing request: " + input.getPath());
        return handler.proxy(input, context);
    }
}
