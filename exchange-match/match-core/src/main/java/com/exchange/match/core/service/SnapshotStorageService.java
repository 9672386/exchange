package com.exchange.match.core.service;

import com.exchange.match.core.model.MatchEngineSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 快照存储服务
 * 负责快照的保存和加载，撮合引擎只负责生成快照文件
 */
@Slf4j
@Service
public class SnapshotStorageService {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${match.snapshot.storage.path:./snapshots}")
    private String snapshotStoragePath;
    
    @Value("${match.snapshot.max-files:100}")
    private int maxSnapshotFiles;
    
    @Value("${match.snapshot.retention-days:30}")
    private int retentionDays;
    
    /**
     * 保存快照到文件
     */
    public String saveSnapshot(MatchEngineSnapshot snapshot) {
        try {
            // 创建存储目录
            Path storageDir = Paths.get(snapshotStoragePath);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
            
            // 生成文件名
            String fileName = generateSnapshotFileName(snapshot);
            Path filePath = storageDir.resolve(fileName);
            
            // 序列化快照
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            
            // 写入文件
            Files.write(filePath, snapshotJson.getBytes());
            
            log.info("快照保存成功: snapshotId={}, file={}", snapshot.getSnapshotId(), filePath);
            
            // 清理旧文件
            cleanupOldSnapshots();
            
            return fileName;
            
        } catch (Exception e) {
            log.error("保存快照失败: snapshotId={}", snapshot.getSnapshotId(), e);
            throw new RuntimeException("保存快照失败", e);
        }
    }
    
    /**
     * 加载最新快照
     */
    public MatchEngineSnapshot loadLatestSnapshot() {
        try {
            Path storageDir = Paths.get(snapshotStoragePath);
            if (!Files.exists(storageDir)) {
                log.info("快照存储目录不存在: {}", storageDir);
                return null;
            }
            
            // 查找最新的快照文件
            Path latestFile = findLatestSnapshotFile(storageDir);
            if (latestFile == null) {
                log.info("未找到快照文件");
                return null;
            }
            
            // 读取并反序列化快照
            String snapshotJson = Files.readString(latestFile);
            MatchEngineSnapshot snapshot = objectMapper.readValue(snapshotJson, MatchEngineSnapshot.class);
            
            log.info("加载快照成功: snapshotId={}, file={}", snapshot.getSnapshotId(), latestFile);
            
            return snapshot;
            
        } catch (Exception e) {
            log.error("加载最新快照失败", e);
            throw new RuntimeException("加载最新快照失败", e);
        }
    }
    
    /**
     * 根据ID加载快照
     */
    public MatchEngineSnapshot loadSnapshotById(String snapshotId) {
        try {
            Path storageDir = Paths.get(snapshotStoragePath);
            if (!Files.exists(storageDir)) {
                log.info("快照存储目录不存在: {}", storageDir);
                return null;
            }
            
            // 查找指定ID的快照文件
            Path snapshotFile = findSnapshotFileById(storageDir, snapshotId);
            if (snapshotFile == null) {
                log.info("未找到快照文件: snapshotId={}", snapshotId);
                return null;
            }
            
            // 读取并反序列化快照
            String snapshotJson = Files.readString(snapshotFile);
            MatchEngineSnapshot snapshot = objectMapper.readValue(snapshotJson, MatchEngineSnapshot.class);
            
            log.info("加载快照成功: snapshotId={}, file={}", snapshot.getSnapshotId(), snapshotFile);
            
            return snapshot;
            
        } catch (Exception e) {
            log.error("加载快照失败: snapshotId={}", snapshotId, e);
            throw new RuntimeException("加载快照失败", e);
        }
    }
    
    /**
     * 获取所有快照文件列表
     */
    public List<SnapshotFileInfo> listSnapshotFiles() {
        try {
            Path storageDir = Paths.get(snapshotStoragePath);
            if (!Files.exists(storageDir)) {
                return List.of();
            }
            
            return Files.list(storageDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(this::createSnapshotFileInfo)
                    .sorted(Comparator.comparing(SnapshotFileInfo::getCreateTime).reversed())
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("获取快照文件列表失败", e);
            throw new RuntimeException("获取快照文件列表失败", e);
        }
    }
    
    /**
     * 删除快照文件
     */
    public boolean deleteSnapshot(String snapshotId) {
        try {
            Path storageDir = Paths.get(snapshotStoragePath);
            if (!Files.exists(storageDir)) {
                return false;
            }
            
            Path snapshotFile = findSnapshotFileById(storageDir, snapshotId);
            if (snapshotFile == null) {
                return false;
            }
            
            Files.delete(snapshotFile);
            log.info("删除快照文件成功: snapshotId={}, file={}", snapshotId, snapshotFile);
            
            return true;
            
        } catch (Exception e) {
            log.error("删除快照文件失败: snapshotId={}", snapshotId, e);
            return false;
        }
    }
    
    /**
     * 生成快照文件名
     */
    private String generateSnapshotFileName(MatchEngineSnapshot snapshot) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("snapshot_%s_%s.json", snapshot.getSnapshotId(), timestamp);
    }
    
    /**
     * 查找最新的快照文件
     */
    private Path findLatestSnapshotFile(Path storageDir) throws IOException {
                    return Files.list(storageDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .orElse(null);
    }
    
    /**
     * 根据ID查找快照文件
     */
    private Path findSnapshotFileById(Path storageDir, String snapshotId) throws IOException {
        return Files.list(storageDir)
                .filter(path -> path.toString().contains(snapshotId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 清理旧快照文件
     */
    private void cleanupOldSnapshots() {
        try {
            Path storageDir = Paths.get(snapshotStoragePath);
            if (!Files.exists(storageDir)) {
                return;
            }
            
            List<Path> snapshotFiles = Files.list(storageDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed())
                    .collect(Collectors.toList());
            
            // 删除超过最大文件数量的旧文件
            if (snapshotFiles.size() > maxSnapshotFiles) {
                for (int i = maxSnapshotFiles; i < snapshotFiles.size(); i++) {
                    Files.delete(snapshotFiles.get(i));
                    log.info("删除旧快照文件: {}", snapshotFiles.get(i));
                }
            }
            
        } catch (Exception e) {
            log.error("清理旧快照文件失败", e);
        }
    }
    
    /**
     * 创建快照文件信息
     */
    private SnapshotFileInfo createSnapshotFileInfo(Path filePath) {
        try {
            SnapshotFileInfo info = new SnapshotFileInfo();
            info.setFileName(filePath.getFileName().toString());
            info.setFilePath(filePath.toString());
            info.setFileSize(Files.size(filePath));
            info.setCreateTime(Files.getLastModifiedTime(filePath).toMillis());
            
            // 尝试解析快照ID
            String content = Files.readString(filePath);
            MatchEngineSnapshot snapshot = objectMapper.readValue(content, MatchEngineSnapshot.class);
            info.setSnapshotId(snapshot.getSnapshotId());
            info.setLastCommandId(snapshot.getLastCommandId());
            
            return info;
        } catch (Exception e) {
            log.warn("解析快照文件信息失败: {}", filePath, e);
            return null;
        }
    }
    
    /**
     * 快照文件信息
     */
    public static class SnapshotFileInfo {
        private String fileName;
        private String filePath;
        private long fileSize;
        private long createTime;
        private String snapshotId;
        private long lastCommandId;
        
        // Getters and Setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        
        public long getCreateTime() { return createTime; }
        public void setCreateTime(long createTime) { this.createTime = createTime; }
        
        public String getSnapshotId() { return snapshotId; }
        public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
        
        public long getLastCommandId() { return lastCommandId; }
        public void setLastCommandId(long lastCommandId) { this.lastCommandId = lastCommandId; }
    }
} 