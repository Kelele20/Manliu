# 漫流 ProGuard 规则
# Room 数据库实体类保留
-keep class com.kelele.manliu.ComicAlbum { *; }
-keep class com.kelele.manliu.ComicPage { *; }
-keep class com.kelele.manliu.ImportJob { *; }
-keep class com.kelele.manliu.ImportItem { *; }

# Room 生成代码
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
