package cn.sixnine.filmlog;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Private, workspace-scoped preview; never an automatic import. */
final class CsvDraft {
    static final class Saved {CsvImport.Preview preview;JSONObject review;}
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"csv-import-preview.json"));}
    static void clear(Context c){file(c).delete();}
    static void save(Context c,CsvImport.Preview p,JSONObject review,boolean importing)throws Exception{
        JSONObject json=new JSONObject().put("version",1).put("importing",importing).put("legacy",p.legacy).put("rows",p.rows).put("manifest",p.data.manifest).put("review",review==null?JSONObject.NULL:review);
        byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>32*1024*1024)throw new IOException("导入预览超过32 MiB");
        AtomicFile target=file(c);FileOutputStream out=null;try{out=target.startWrite();out.write(bytes);target.finishWrite(out);}catch(Exception e){if(out!=null)target.failWrite(out);throw e;}
    }
    static Saved load(Context c)throws Exception{
        AtomicFile target=file(c);byte[] bytes;
        try(InputStream in=target.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>32*1024*1024)throw new IOException("导入预览过大");out.write(buffer,0,n);}bytes=out.toByteArray();}catch(FileNotFoundException absent){return null;}
        JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(json.getInt("version")!=1)throw new IOException("不支持的导入草稿版本");
        // The process may die after the database commits: never offer the same batch again automatically.
        if(json.getBoolean("importing"))throw new IOException("上次导入被中断，结果需先在胶卷列表核对，避免重复导入");
        Saved result=new Saved();result.preview=new CsvImport.Preview();result.preview.data=new FilmBackup.Prepared();result.preview.data.manifest=json.getJSONObject("manifest");if(result.preview.data.manifest.getInt("format")!=1||result.preview.data.manifest.getInt("schema")<5||result.preview.data.manifest.getInt("schema")>24)throw new IOException("不支持的导入预览版本");FilmBackup.normalizeCatalog(result.preview.data.manifest);result.preview.legacy=json.getBoolean("legacy");result.preview.rows=json.getInt("rows");FilmBackup.validate(result.preview.data);
        result.review=json.isNull("review")?null:json.getJSONObject("review");return result;
    }
}
