# SmartDosing R8 规则
# Gson、Retrofit、Room、OkHttp、kotlinx-coroutines 自带 consumer rules，这里只补项目相关部分。

# ---- 通用 ----
# 泛型签名与注解供 Gson/Retrofit 解析类型；行号用于配合 mapping.txt 还原崩溃堆栈
-keepattributes Signature,*Annotation*,SourceFile,LineNumberTable,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ---- JSON 模型 ----
# 字段名已通过 @SerializedName 固定，Gson 自带规则会保留这些字段。
# 枚举按常量名序列化（局域网协议、回调载荷），数据库也以字符串保存枚举值并用 valueOf 读回，
# 常量名一旦被混淆就会和已有数据、后端协议对不上，所以本应用的枚举字段一律保留原名。
-keepclassmembers enum com.example.smartdosing.** {
    <fields>;
}

# ---- Ktor / Netty ----
# Netty 通过反射调用无参构造创建服务端 Channel
-keep class io.netty.channel.socket.nio.NioServerSocketChannel { <init>(); }
-keep class io.netty.channel.socket.nio.NioSocketChannel { <init>(); }
# JCTools 队列按字段名取 Unsafe 偏移量，字段被重命名会在启动服务时崩溃
-keepclassmembers class io.netty.util.internal.shaded.org.jctools.** { <fields>; }
-keepclassmembernames class io.netty.buffer.AbstractByteBufAllocator { *; }
-keepclassmembernames class io.netty.buffer.AdvancedLeakAwareByteBuf { *; }
-keepclassmembernames class io.netty.util.ReferenceCountUtil { *; }
# Netty 用反射检查 @Sharable（可继承注解）决定 handler 能否复用；注解被剥离后第二个连接起全部失败
-keep @interface io.netty.channel.ChannelHandler$Sharable
-keep,allowobfuscation,allowshrinking @io.netty.channel.ChannelHandler$Sharable class *
# TypeParameterMatcher 通过反射读取编解码基类的泛型参数（如 MessageToMessageEncoder<I>）。
# R8 full mode 只给被 keep 的类保留 Signature，缺了它每个 HTTP 请求都会报 unknown type parameter 'I'。
-keep,allowobfuscation class io.netty.channel.SimpleChannelInboundHandler
-keep,allowobfuscation class io.netty.channel.SimpleUserEventChannelHandler
-keep,allowobfuscation class io.netty.handler.codec.MessageToMessageEncoder
-keep,allowobfuscation class io.netty.handler.codec.MessageToMessageDecoder
-keep,allowobfuscation class io.netty.handler.codec.MessageToMessageCodec
-keep,allowobfuscation class io.netty.handler.codec.MessageToByteEncoder
-keep,allowobfuscation class io.netty.handler.codec.ByteToMessageCodec
-keep,allowobfuscation,allowshrinking class * extends io.netty.channel.SimpleChannelInboundHandler
-keep,allowobfuscation,allowshrinking class * extends io.netty.channel.SimpleUserEventChannelHandler
-keep,allowobfuscation,allowshrinking class * extends io.netty.handler.codec.MessageToMessageEncoder
-keep,allowobfuscation,allowshrinking class * extends io.netty.handler.codec.MessageToMessageDecoder
-keep,allowobfuscation,allowshrinking class * extends io.netty.handler.codec.MessageToMessageCodec
-keep,allowobfuscation,allowshrinking class * extends io.netty.handler.codec.MessageToByteEncoder
-keep,allowobfuscation,allowshrinking class * extends io.netty.handler.codec.ByteToMessageCodec

# Netty/Ktor 引用了 Android 上不存在的可选依赖（日志框架、BlockHound、原生传输等）
-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.jboss.marshalling.**
-dontwarn sun.security.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
