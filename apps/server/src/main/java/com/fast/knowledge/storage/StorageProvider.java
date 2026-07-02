package com.fast.knowledge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 文档二进制存储 SPI，默认本地文件系统实现。
 */
public interface StorageProvider {

    StoredObject storeUpload(Long kbId, org.springframework.web.multipart.MultipartFile file) throws IOException;

    StoredObject storeText(Long kbId, String fileName, String content) throws IOException;

    void delete(String filePath) throws IOException;

    Path readablePath(String filePath);

    InputStream openInputStream(String filePath) throws IOException;
}
