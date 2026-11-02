package cn.sixnine.filmlog;

import java.io.*;
import java.util.*;

final class CsvRecords {
    static List<List<String>> readAuto(Reader source)throws IOException{
        PushbackReader in=new PushbackReader(source,65536);StringBuilder header=new StringBuilder();boolean quoted=false;int commas=0,tabs=0,semicolons=0,n;
        while((n=in.read())!=-1){header.append((char)n);if(header.length()>=65536)throw new IOException("表头过长");if(n=='"')quoted=!quoted;if(!quoted){if(n==',')commas++;if(n=='\t')tabs++;if(n==';')semicolons++;if(n=='\n'||n=='\r')break;}}
        in.unread(header.toString().toCharArray());return read(in,tabs>commas&&tabs>=semicolons?'\t':semicolons>commas?';':',');
    }
    static List<List<String>> read(Reader source)throws IOException{return read(source,',');}
    private static List<List<String>> read(Reader source,char delimiter)throws IOException{
        PushbackReader in=new PushbackReader(source,1);ArrayList<List<String>> rows=new ArrayList<>();ArrayList<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();int state=0;long chars=0;boolean touched=false;int n=in.read();if(n!=0xfeff&&n!=-1)in.unread(n);
        while((n=in.read())!=-1){if(++chars>8*1024*1024)throw new IOException("CSV 超过 8 MiB 字符限制");char c=(char)n;
            if(state==2){if(c=='"')state=3;else field.append(c);touched=true;continue;}
            if(state==3&&c=='"'){field.append('"');state=2;continue;}
            if(c==delimiter||c=='\r'||c=='\n'){
                row.add(field.toString());field.setLength(0);state=0;touched=c==delimiter;if(row.size()>256)throw new IOException("CSV 列数过多");
                if(c!=delimiter){rows.add(row);row=new ArrayList<>();if(rows.size()>100000)throw new IOException("CSV 行数过多");if(c=='\r'){int next=in.read();if(next!='\n'&&next!=-1)in.unread(next);}}continue;
            }
            if(state==3)throw new IOException("CSV 引号后存在非法字符");
            if(c=='"'){if(state!=0)throw new IOException("CSV 引号位置无效");state=2;}else{field.append(c);state=1;}touched=true;
        }
        if(state==2)throw new IOException("CSV 引号未闭合");if(touched||!row.isEmpty()){row.add(field.toString());rows.add(row);}return rows;
    }
}
