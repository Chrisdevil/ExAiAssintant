# ExAiAssistant

专属个人 AI 对话助手，调用 DeepSeek API，支持跨对话记忆。

## 技术栈
- **后端**: Spring Boot 4.0.6, Java 17, Maven
- **前端**: React + TypeScript（待搭建）
- **API**: DeepSeek API（OpenAI 兼容格式）
- **部署**: 先本地开发，后续上云

## 核心功能

### 1. AI 对话
- 调用 DeepSeek API，用户自己的 token
- 流式输出
- Markdown 渲染 + 代码高亮（前端）

### 2. 跨对话记忆（核心难点）
- 需要构建记忆系统，API 本身无此功能
- 方案：摘要 + 向量检索混合
  - 对话结束后自动提取关键信息摘要，存入 DB
  - 新对话开始时检索相关记忆，注入 system prompt
  - 向量化存入向量数据库做语义检索
- 效果示例：「找工作失利」+「同事是傻逼」→ 能在「精神压力大」的新对话中关联历史

### 3. 历史对话管理
- 查看、搜索历史对话
- AI 自动打标签（对话分类）
- 全文搜索或向量搜索

### 4. 文件上传
- 图片等多模态输入（需确认 DeepSeek 账号是否开通多模态）
- 文件解析

### 5. 单用户
- 只有用户自己使用
- 无需鉴权系统

## 数据库
- 开发阶段可先用 H2（嵌入式）或 SQLite（持久化文件），待确认

## 项目当前状态
- 刚用 Spring Initializr 生成，pom.xml 只有 spring-boot-starter 基础依赖
- 待添加：Web、WebFlux（流式）、数据库、向量存储等依赖
- 前端待搭建

## 用户偏好
- 用户有 Java 经验，前端不熟
- 用户用 DeepSeek API，有自己的 token
- 先本地跑通，再部署云
