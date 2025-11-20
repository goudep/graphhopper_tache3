# 如何演示 Mutation Score 下降

## 步骤 1: 建立 Baseline (3%)

首先，push 当前代码建立 baseline：
```bash
git add .
git commit -m "Establish baseline mutation score"
git push
```

等待 GitHub Actions 完成，确认 score 为 3%。

## 步骤 2: 降低 Mutation Score

有几种方法可以降低 score：

### 方法 1: 删除关键断言（推荐）

在 `testRoutePost_HappyPath()` 中，注释掉或删除这些验证：

```java
// 删除这些行来降低 score:
verify(mockProfileResolver, times(1)).resolveProfile(any());
verify(mockGHRequestTransformer, times(1)).transformRequest(any(GHRequest.class));
assertEquals("car", capturedRequest.getProfile(), "Profile should be 'car'");
assertNotNull(capturedRequest.getPoints(), "Points should not be null");
assertEquals(2, capturedRequest.getPoints().size(), "Should have 2 points");
```

### 方法 2: 注释掉整个测试用例

注释掉 `testRoutePost_GraphHopperConfig_ReadsSnapPreventionsDefault()` 测试。

### 方法 3: 删除断言但保留测试结构

只保留基本的 HTTP status 检查，删除所有 verify 和详细断言。

## 步骤 3: Push 并观察 Rickroll

```bash
git add .
git commit -m "Demonstrate mutation score drop - testing rickroll"
git push
```

GitHub Actions 应该会：
1. 检测到 score 从 3% 下降到更低的值
2. Workflow 失败
3. 显示 rickroll 消息

## 步骤 4: 恢复代码

演示完成后，恢复删除的代码：
```bash
git checkout HEAD~1 -- web-bundle/src/test/java/com/graphhopper/resources/RouteResourceMockTest.java
# 或者手动恢复删除的代码
```

## 预期结果

- **Before**: Mutation Score: 3%
- **After**: Mutation Score: ~1-2% (取决于删除了多少断言)
- **Workflow**: ❌ Failed with rickroll message

