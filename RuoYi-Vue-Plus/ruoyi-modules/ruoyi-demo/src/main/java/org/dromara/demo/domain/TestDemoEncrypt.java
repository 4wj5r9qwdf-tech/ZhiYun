package org.dromara.demo.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.encrypt.annotation.EncryptField;

/**
 * 测试加密字段实体。
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_demo")
public class TestDemoEncrypt extends TestDemo {

    /**
     * key键
     */
    @EncryptField
    private String testKey;

    /**
     * 值
     */
    @EncryptField
    private String value;

}
