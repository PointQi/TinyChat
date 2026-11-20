混排是渲染里最容易翻车的一类：中文与拉丁字母的字宽、标点、行距都不一样，行内代码一带底色就更明显。

先看一句正常的中英混排：把 `RecyclerView` 换成 `LazyColumn` 之后，第一屏的测量次数从 3 次降到 1 次，**滑动时不再掉帧**。这句话里同时有全角标点、半角标点、粗体和行内代码。

再看全角标点密集的情况：「你说得对，但是这个方案在 3.0 版里已经改过了。」他说完就把窗口关了——没有留下任何记录。破折号、省略号、书名号《渲染原理》、引号“双引号”与‘单引号’都放进来。

表情符号单独一行：

🎉 🚀 ✅ ⚠️ 🐳

夹在正文里的表情：这一步大约要 2 分钟 ⏱，做完会看到绿色的勾 ✅，如果没有就回去看日志 🔍。

数字与单位、英文缩写混排：单帧预算 16.6ms，90 分位要压到 8ms 以内；用 `adb shell dumpsys gfxinfo` 取 Janky frames 的比例，目标是低于 1%。

最后一行是一段很长的英文，用来验证换行位置是否会把单词从中间掰断：The quick brown fox jumps over the lazy dog while the renderer decides whether to break at a space, a hyphen, or a CJK boundary.
