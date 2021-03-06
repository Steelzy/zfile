package im.zhaojun.zfile.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.crypto.SecureUtil;
import im.zhaojun.zfile.cache.ZFileCache;
import im.zhaojun.zfile.config.StorageTypeFactory;
import im.zhaojun.zfile.model.constant.SystemConfigConstant;
import im.zhaojun.zfile.model.dto.SystemConfigDTO;
import im.zhaojun.zfile.model.entity.SystemConfig;
import im.zhaojun.zfile.model.enums.StorageTypeEnum;
import im.zhaojun.zfile.repository.SystemConfigRepository;
import im.zhaojun.zfile.service.base.AbstractBaseFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhaojun
 */
@Slf4j
@Service
public class SystemConfigService {

    @Resource
    private ZFileCache zFileCache;

    @Resource
    private SystemConfigRepository systemConfigRepository;

    private Class<SystemConfigDTO> systemConfigClazz = SystemConfigDTO.class;

    public SystemConfigDTO getSystemConfig() {
        SystemConfigDTO cacheConfig = zFileCache.getConfig();
        if (cacheConfig != null) {
            return cacheConfig;
        }

        SystemConfigDTO systemConfigDTO = new SystemConfigDTO();
        List<SystemConfig> systemConfigList = systemConfigRepository.findAll();

        for (SystemConfig systemConfig : systemConfigList) {
            String key = systemConfig.getKey();

            try {
                Field field = systemConfigClazz.getDeclaredField(key);
                if (field != null) {
                    field.setAccessible(true);
                    String strVal = systemConfig.getValue();
                    Object convertVal = Convert.convert(field.getType(), strVal);
                    field.set(systemConfigDTO, convertVal);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                if (log.isDebugEnabled()) {
                    log.debug("通过反射, 将字段 {" + key + "}注入 SystemConfigDTO 时出现异常:", e);
                }
            }
        }

        zFileCache.updateConfig(systemConfigDTO);
        return systemConfigDTO;
    }


    public void updateSystemConfig(SystemConfigDTO systemConfigDTO) throws Exception {
        List<SystemConfig> systemConfigList = new ArrayList<>();

        Field[] fields = systemConfigClazz.getDeclaredFields();
        for (Field field : fields) {
            String key = field.getName();
            SystemConfig systemConfig = systemConfigRepository.findByKey(key);
            if (systemConfig != null) {
                field.setAccessible(true);
                Object val = field.get(systemConfigDTO);
                if (val != null) {
                    systemConfig.setValue(val.toString());
                    systemConfigList.add(systemConfig);
                }
            }
        }

        zFileCache.removeConfig();
        systemConfigRepository.saveAll(systemConfigList);
    }


    public void updateUsernameAndPwd(String username, String password) {
        SystemConfig usernameConfig = systemConfigRepository.findByKey(SystemConfigConstant.USERNAME);
        usernameConfig.setValue(username);
        systemConfigRepository.save(usernameConfig);

        String encryptionPassword = SecureUtil.md5(password);
        SystemConfig systemConfig = systemConfigRepository.findByKey(SystemConfigConstant.PASSWORD);
        systemConfig.setValue(encryptionPassword);

        zFileCache.removeConfig();

        systemConfigRepository.save(systemConfig);
    }


    public void updateCacheEnableConfig(Boolean isEnable) {
        SystemConfig enableConfig = systemConfigRepository.findByKey(SystemConfigConstant.ENABLE_CACHE);
        enableConfig.setValue(isEnable.toString());
        systemConfigRepository.save(enableConfig);
        zFileCache.removeConfig();
    }


    public AbstractBaseFileService getCurrentFileService() {
        StorageTypeEnum storageStrategy = getCurrentStorageStrategy();
        return StorageTypeFactory.getStorageTypeService(storageStrategy);
    }


    public StorageTypeEnum getCurrentStorageStrategy() {
        SystemConfigDTO systemConfigDTO = getSystemConfig();
        return systemConfigDTO.getStorageStrategy();
    }

    public boolean getEnableCache() {
        SystemConfigDTO systemConfigDTO = getSystemConfig();
        return BooleanUtil.isTrue(systemConfigDTO.getEnableCache());
    }

    public boolean getSearchIgnoreCase() {
        SystemConfigDTO systemConfigDTO = getSystemConfig();
        return BooleanUtil.isTrue(systemConfigDTO.getSearchIgnoreCase());
    }


}