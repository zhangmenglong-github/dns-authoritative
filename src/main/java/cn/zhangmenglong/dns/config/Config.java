package cn.zhangmenglong.dns.config;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;

public class Config {

    //储存配置文件信息
    public static Map<String, Object> params = null;

    public Config() {
        //创建yaml文件读取对象
        Yaml yaml = new Yaml();

        //读取衙门配置文件并储存
        params = yaml.load(this.getClass().getClassLoader().getResourceAsStream("config.yaml"));
    }
}
