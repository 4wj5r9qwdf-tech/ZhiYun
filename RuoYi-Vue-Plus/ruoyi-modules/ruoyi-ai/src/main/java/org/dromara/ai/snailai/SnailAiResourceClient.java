package org.dromara.ai.snailai;

import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiResourceResponse;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiResourceUploadRequest;
import com.aizuda.snail.ai.openapi.client.core.annotation.OpenApiMapping;

/** 调用 SnailAI 原生资源上传接口。 / Client for SnailAI's native resource upload API. */
public interface SnailAiResourceClient {

    @OpenApiMapping(path = "/openapi/v1/resource/upload", method = OpenApiMapping.HttpMethod.POST)
    Result<OpenApiResourceResponse> upload(OpenApiResourceUploadRequest request);
}
