package com.tianji.learning.cionfig;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.tianji.learning.utils.TableInfoContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MybatisConfiguration {

    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor() {

        // 准备一个 Map, 用于存储 TableNameHandler
        Map<String, TableNameHandler> map = new HashMap<>(1);
        // 存入一个 TableNameHandler，用来替换points_board表名
        // 替换方式，就是从 TableInfoContext 中读取保存好的动态表名
        map.put("points_board", (sql, tableName) ->
            TableInfoContext.getInfo() == null ? tableName : TableInfoContext.getInfo()
        );
        map.put("points_record", (sql, tableName) ->
                TableInfoContext.getInfo() == null ? tableName : TableInfoContext.getInfo()
        );
        return new DynamicTableNameInnerInterceptor(map);
    }

}
