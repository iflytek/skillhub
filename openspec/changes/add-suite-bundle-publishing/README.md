# add-suite-bundle-publishing

本变更将 Issue #847 整理为建立在 Issue #715 / PR #828 Suite 模型之上的三组交付能力：
创建与更新共用的 Suite 导入编排、任意成员 Skill 的所属 Suite 展示，以及 Suite 展示信息与标签。

提案明确记录 #847 中哪些需求继续保留、哪些需要调整。用户可以从技能市场组合精确版本，也可以
通过一个 ZIP 或受支持浏览器中的根目录，一次提交多个 Skill 文件夹来创建或更新 Suite。导入可以
在有权限的 Namespace 中创建新 Skill，也可以更新操作者有权发布的已有 Skill；非本人所有 Skill
只能作为精确 PUBLISHED 版本引用。技能市场和套件专区继续分开。Suite 拥有自己的展示信息和标签，
但不修改成员 Skill 的标签或版本 Tag。
