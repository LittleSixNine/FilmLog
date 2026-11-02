package cn.sixnine.filmlog;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.time.Instant;

public class MediaInstrumentation extends Instrumentation {
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){waitForIdleSync();Bundle result=new Bundle();try{
  check(getTargetContext().getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolation");
  android.graphics.Paint posterPaint=new android.graphics.Paint();
  float posterSize=StatsPoster.fittedSize(posterPaint,"2147483647",82,308);posterPaint.setTextSize(posterSize);
  check(posterPaint.measureText("2147483647")<=308.01f,"poster large count fits column");
  check(StatsPoster.fittedSize(posterPaint,"6",82,308)==82,"poster small count retains size");
  StatsStore.Summary posterSummary=new StatsStore.Summary();posterSummary.year=2026;posterSummary.frames=Integer.MAX_VALUE;posterSummary.rolls=Integer.MAX_VALUE;posterSummary.days=Integer.MAX_VALUE;posterSummary.months[9]=Integer.MAX_VALUE;
  ByteArrayOutputStream posterOutput=new ByteArrayOutputStream();StatsPoster.write(posterSummary,true,posterOutput);
  Bitmap posterBitmap=BitmapFactory.decodeByteArray(posterOutput.toByteArray(),0,posterOutput.size());check(posterBitmap.getWidth()==1200&&posterBitmap.getHeight()==1600,"poster dimensions");posterBitmap.recycle();
  File source=new File(getTargetContext().getCacheDir(),"input.jpg");
  Bitmap bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);
  for(int y=0;y<24;y++)for(int x=0;x<32;x++)bitmap.setPixel(x,y,0xff000000|(x*7<<16)|(y*9<<8)|50);
  try(OutputStream out=new FileOutputStream(source)){check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,out),"fixture");}
  ExifInterface original=new ExifInterface(source.toString());
  original.setAttribute("GPSLatitude","31/1,0/1,0/1");original.setAttribute("GPSLatitudeRef","N");
  original.setAttribute("GPSLongitude","121/1,0/1,0/1");original.setAttribute("GPSLongitudeRef","E");
  original.setAttribute("Orientation","6");original.saveAttributes();
  byte[] jpeg=Files.readAllBytes(source.toPath());
  byte[] secret="http://ns.adobe.com/xap/1.0/\0OLD_PRIVATE_GPS_31_121".getBytes("UTF-8");
  try(DataOutputStream out=new DataOutputStream(new FileOutputStream(source))){out.write(jpeg,0,2);out.writeShort(0xffe1);out.writeShort(secret.length+2);out.write(secret);out.write(jpeg,2,jpeg.length-2);}
  byte[] before=Files.readAllBytes(source.toPath());
  LogStore.Roll r=new LogStore.Roll();r.camera="测试相机";r.stock="Test Stock";r.iso=400;
  LogStore.Shot s=new LogStore.Shot();s.captured=Instant.parse("2026-10-21T15:59:55.123Z").toEpochMilli();s.zone=480;s.memo="街头 & 夜景 <测试>";s.lens="50mm";s.aperture="5.6";s.shutter="1/125";s.lat=-30.25;s.lon=120.5;s.locationTime=s.captured+10000;
  for(boolean gps:new boolean[]{false,true}){
   File output=ScanExport.create(getTargetContext(),source,r,s,gps);ExifInterface exif=new ExifInterface(output.toString());
   check(Arrays.equals(before,Files.readAllBytes(source.toPath())),"input unchanged");
   check(BitmapFactory.decodeFile(source.toString()).sameAs(BitmapFactory.decodeFile(output.toString())),"decoded pixels identical");
   check(exif.getAttributeInt("Orientation",0)==6,"orientation");
   check("2026:10:21 23:59:55".equals(exif.getAttribute("DateTimeOriginal")),"adjusted cross-day time");
   check("+08:00".equals(exif.getAttribute("OffsetTimeOriginal")),"timezone");
   check("123".equals(exif.getAttribute("SubSecTimeOriginal")),"milliseconds");
   check(!new String(Files.readAllBytes(output.toPath()),"ISO-8859-1").contains("OLD_PRIVATE_GPS"),"old XMP removed");
   float[] latlon=new float[2];check(exif.getLatLong(latlon)==gps,"GPS switch");
   if(gps){check(Math.abs(latlon[0]+30.25)<0.00001&&Math.abs(latlon[1]-120.5)<0.00001,"new coordinates");check("16:00:05".equals(exif.getAttribute("GPSTimeStamp")),"GPS fix retains actual time");}
   String xml=new String(Files.readAllBytes(output.toPath()),"UTF-8");check(xml.contains("测试相机")&&xml.contains("街头 &amp; 夜景 &lt;测试&gt;"),"Unicode XMP escaped and retained");
   Files.copy(output.toPath(),new File(getTargetContext().getFilesDir(),gps?"gps-on.jpg":"gps-off.jpg").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   output.delete();
  }
  s.aperture="Auto";s.shutter="B";
  File symbolic=ScanExport.create(getTargetContext(),source,r,s,false);ExifInterface symbolicExif=new ExifInterface(symbolic.toString());
  check(symbolicExif.getAttribute(ExifInterface.TAG_F_NUMBER)==null&&symbolicExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)==null,"Auto/B must not invent numeric EXIF");
  check(Arrays.equals(before,Files.readAllBytes(source.toPath())),"symbolic export input unchanged");symbolic.delete();s.aperture="5.6";s.shutter="1/125";
  for(String longValue:new String[]{"2","2s",PhotoMath.shutterChoice(2.5)}){s.shutter=longValue;File longExposure=ScanExport.create(getTargetContext(),source,r,s,false);ExifInterface longExif=new ExifInterface(longExposure.toString());check(Math.abs(longExif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME,-1)-PhotoMath.seconds(longValue))<.000001,"legacy and canonical long shutter EXIF");check(Arrays.equals(before,Files.readAllBytes(source.toPath())),"long exposure export preserves source");longExposure.delete();}s.shutter="1/125";
  getTargetContext().deleteDatabase("film-log.db");
  LogStore logs=new LogStore(getTargetContext());
  String roll=logs.addRoll("Scans","Test","Camera",400,36);
  String shot1=logs.insert(roll,10000,-10,480,"denied","","","","","","");
  String shot2=logs.insert(roll,20000,-10,480,"denied","","","","","","");
  String other=logs.addRoll("Other","Test","Camera",400,36);
  String foreign=logs.insert(other,30000,-10,480,"denied","","","","","","");
  ScanStore scans=new ScanStore(getTargetContext(),logs);
  ScanStore.Scan a,b;
  try(InputStream in=new FileInputStream(source)){a=scans.importJpeg(roll,"same.jpg",in);}
  try(InputStream in=new FileInputStream(source)){b=scans.importJpeg(roll,"same.jpg",in);}
  check(!a.id.equals(b.id)&&a.sha256.equals(b.sha256),"same names cannot overwrite");
  check(Arrays.equals(before,Files.readAllBytes(scans.file(a.id).toPath())),"import exact copy");
  java.util.Map<String,String> links=new java.util.LinkedHashMap<>();links.put(a.id,shot1);links.put(b.id,shot2);scans.associate(roll,links);
  if(android.os.Build.VERSION.SDK_INT>=29){
   android.content.ContentResolver resolver=getTargetContext().getContentResolver();
   java.util.Set<Long> oldIds=new java.util.HashSet<>();
   try(android.database.Cursor c=resolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},null,null,null)){while(c.moveToNext())oldIds.add(c.getLong(0));}
   check(AlbumOutput.save(getTargetContext(),logs,roll,false)==2,"album count");
   int albumCount=0;
   try(android.database.Cursor c=resolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","is_pending","datetaken","relative_path"},null,null,null)){
    while(c.moveToNext())if(!oldIds.contains(c.getLong(0))){
     android.net.Uri uri=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));
     try{check(c.getInt(1)==0,"album published");check(c.getLong(2)==0||c.getLong(2)==10000,"album adjusted date");check(c.getString(3).equals("Pictures/胶片随记/"),"album folder");
      try(InputStream in=resolver.openInputStream(uri)){ExifInterface e=new ExifInterface(in);check(!e.getLatLong(new float[2]),"album GPS off");check(e.getAttribute("DateTimeOriginal")!=null,"album EXIF");}albumCount++;
     }finally{resolver.delete(uri,null,null);}
    }
   }
   check(albumCount==2,"album files read back");
   File held=new File(scans.file(b.id).toString()+".held");check(scans.file(b.id).renameTo(held),"simulate missing second scan");
   try{try{AlbumOutput.save(getTargetContext(),logs,roll,false);throw new AssertionError("partial album accepted");}catch(IOException expected){}}
   finally{check(held.renameTo(scans.file(b.id)),"restore second scan");}
   try(android.database.Cursor c=resolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},null,null,null)){while(c.moveToNext())check(oldIds.contains(c.getLong(0)),"failed album rollback");}
   android.content.ContentValues pending=new android.content.ContentValues();pending.put("_display_name","interrupted-test.jpg");pending.put("mime_type","image/jpeg");pending.put("is_pending",1);
   android.net.Uri abandoned=resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,pending);
   check(getTargetContext().getSharedPreferences("album-export",0).edit().putStringSet("uris",java.util.Collections.singleton(abandoned.toString())).commit(),"journal simulated");
   AlbumOutput.recover(getTargetContext());
   try(android.database.Cursor c=resolver.query(abandoned,new String[]{"_id"},null,null,null)){check(c==null||c.getCount()==0,"interrupted album cleaned");}
  }
  links.put(a.id,shot2);links.put(b.id,shot1);scans.associate(roll,links);
  check(scans.list(roll).size()==2,"swap preserved assets");
  links.put(a.id,shot1);
  try{scans.associate(roll,links);throw new AssertionError("duplicate association");}catch(IllegalArgumentException expected){}
  for(ScanStore.Scan scan:scans.list(roll))check(scan.shotId.equals(scan.id.equals(a.id)?shot2:shot1),"duplicate rollback");
  links.put(a.id,foreign);
  try{scans.associate(roll,links);throw new AssertionError("cross roll association");}catch(IllegalArgumentException expected){}
  links.remove(a.id);
  try{scans.associate(roll,links);throw new AssertionError("stale preview");}catch(IllegalArgumentException expected){}
  try{scans.importJpeg(roll,"bad.jpg",new ByteArrayInputStream(new byte[]{1,2,3}));throw new AssertionError("bad import accepted");}catch(IOException expected){}
  check(scans.list(roll).size()==2,"bad import no row");
  scans.remove(other,a.id);check(scans.file(a.id).exists(),"wrong roll cannot remove");
  scans.remove(roll,a.id);check(scans.file(a.id).exists()&&scans.list(roll).size()==1&&scans.list(roll,true).size()==1,"move to recently deleted");
  check(Arrays.equals(before,Files.readAllBytes(source.toPath())),"removal preserves source");
  try(InputStream in=new FileInputStream(source)){try{scans.importJpeg(roll,"retry-deleted.jpg",in,a.id);throw new AssertionError("retry overwrote deleted asset");}catch(IOException expected){}}
  check(Arrays.equals(before,Files.readAllBytes(scans.file(a.id).toPath())),"deleted asset retry preserves private file");
  check(scans.restore(roll,a.id),"restore free association");
  try{scans.replace(roll,a.id,b.id);throw new AssertionError("replace associated target");}catch(IllegalArgumentException expected){}
  ScanStore.Scan replacement;try(InputStream in=new FileInputStream(source)){replacement=scans.importJpeg(roll,"replacement.jpg",in);}
  scans.replace(roll,a.id,replacement.id);
  check(scans.list(roll,true).size()==1&&scans.file(a.id).exists(),"replace preserves old copy in trash");
  check(!scans.restore(roll,a.id),"restore occupied frame stays unassigned");
  for(ScanStore.Scan scan:scans.list(roll))if(scan.id.equals(a.id))check(scan.shotId.isEmpty(),"no stolen association");
  scans.remove(roll,a.id);scans.remove(roll,replacement.id);

  logs.close();logs=new LogStore(getTargetContext());scans=new ScanStore(getTargetContext(),logs);
  check(scans.list(roll).size()==1&&scans.list(roll).get(0).shotId.equals(shot1),"association survives reopen");logs.close();
  logs=new LogStore(getTargetContext());
  logs.costs(roll,"12.34","5.60","USD");
  ByteArrayOutputStream archive=new ByteArrayOutputStream();FilmBackup.write(getTargetContext(),logs,archive);
  int beforeRolls=logs.rolls().size();
  try(FilmBackup.Prepared backup=FilmBackup.inspect(getTargetContext(),new ByteArrayInputStream(archive.toByteArray()))){
   check(backup.count("rolls")==2&&backup.count("shots")==3&&backup.count("scans")==3,"archive includes active and deleted scans");
   FilmBackup.restore(getTargetContext(),logs,backup);check(logs.rolls().size()==beforeRolls+2,"restore appends without overwrite");
   int active=0,deleted=0;ScanStore restored=new ScanStore(getTargetContext(),logs);
   for(LogStore.Roll rr:logs.rolls()){active+=restored.list(rr.id).size();deleted+=restored.list(rr.id,true).size();}
   check(active==2&&deleted==4,"restore includes trash and media links");
   int costCopies=0;for(LogStore.Roll rr:logs.rolls())if(rr.currency.equals("USD")&&rr.filmCost.equals("12.34")&&rr.labCost.equals("5.60"))costCopies++;check(costCopies==2,"backup costs and currency roundtrip");
   org.json.JSONObject zoneShot=backup.manifest.getJSONArray("shots").getJSONObject(0);Object originalZone=zoneShot.get("zone_offset_minutes");int zoneFileCount=new File(getTargetContext().getFilesDir(),"scans").list().length;
   for(Object badZone:new Object[]{Integer.MIN_VALUE,-1081,1081,4294967296L,480.5,"480"}){zoneShot.put("zone_offset_minutes",badZone);try{FilmBackup.restore(getTargetContext(),logs,backup);throw new AssertionError("invalid zone accepted: "+badZone);}catch(IOException expected){}check(logs.rolls().size()==beforeRolls+2&&new File(getTargetContext().getFilesDir(),"scans").list().length==zoneFileCount,"bad zone leaves rows and media unchanged");}zoneShot.put("zone_offset_minutes",originalZone);
   for(int validZone:new int[]{-1080,1080})try(FilmBackup.Prepared accepted=FilmBackup.inspect(getTargetContext(),new ByteArrayInputStream(archiveWithZone(archive.toByteArray(),validZone)))){check(accepted.manifest.getJSONArray("shots").getJSONObject(0).getInt("zone_offset_minutes")==validZone,"boundary zone accepted");}
   String oldName=backup.manifest.getJSONArray("rolls").getJSONObject(0).getString("name");
   backup.manifest.getJSONArray("rolls").getJSONObject(0).remove("name");
   int fileCount=new File(getTargetContext().getFilesDir(),"scans").list().length;
   try{FilmBackup.restore(getTargetContext(),logs,backup);throw new AssertionError("missing field accepted");}catch(IOException expected){}
   check(logs.rolls().size()==beforeRolls+2&&new File(getTargetContext().getFilesDir(),"scans").list().length==fileCount,"failed restore rollback and file cleanup");
   backup.manifest.getJSONArray("rolls").getJSONObject(0).put("name",oldName);
   File corrupt=backup.files.values().iterator().next();try(FileOutputStream out=new FileOutputStream(corrupt)){out.write(1);}
   try{FilmBackup.restore(getTargetContext(),logs,backup);throw new AssertionError("corrupt media accepted");}catch(IOException expected){}
   check(logs.rolls().size()==beforeRolls+2,"corrupt archive preserves existing data");
  }
  ByteArrayOutputStream legacyArchive=new ByteArrayOutputStream();
  try(java.util.zip.ZipInputStream in=new java.util.zip.ZipInputStream(new ByteArrayInputStream(archive.toByteArray()));java.util.zip.ZipOutputStream out=new java.util.zip.ZipOutputStream(legacyArchive)){
   java.util.zip.ZipEntry entry;while((entry=in.getNextEntry())!=null){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;while((read=in.read(buffer))!=-1)bytes.write(buffer,0,read);byte[] data=bytes.toByteArray();
    if(entry.getName().equals("manifest.json")){org.json.JSONObject manifest=new org.json.JSONObject(new String(data,"UTF-8"));manifest.put("schema",6);org.json.JSONArray oldRolls=manifest.getJSONArray("rolls");for(int i=0;i<oldRolls.length();i++){oldRolls.getJSONObject(i).remove("film_cost");oldRolls.getJSONObject(i).remove("cost_currency");}data=manifest.toString().getBytes("UTF-8");}
    out.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));out.write(data);out.closeEntry();
   }
  }
  try(FilmBackup.Prepared oldBackup=FilmBackup.inspect(getTargetContext(),new ByteArrayInputStream(legacyArchive.toByteArray()))){check(oldBackup.manifest.getInt("schema")==25&&oldBackup.manifest.getJSONArray("rolls").getJSONObject(0).getString("cost_currency").isEmpty(),"legacy backup adds unknown currency");FilmBackup.restore(getTargetContext(),logs,oldBackup);}
  ByteArrayOutputStream attack=new ByteArrayOutputStream();try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(attack)){zip.putNextEntry(new java.util.zip.ZipEntry("../outside"));zip.write(1);zip.closeEntry();}
  try{FilmBackup.inspect(getTargetContext(),new ByteArrayInputStream(attack.toByteArray()));throw new AssertionError("zip traversal accepted");}catch(IOException expected){}
  logs.close();
  logs=new LogStore(getTargetContext());
  File rollZip=new File(getTargetContext().getFilesDir(),"roll.zip");try(OutputStream out=new FileOutputStream(rollZip)){check(RollOutput.zip(getTargetContext(),logs,roll,false,out)==1,"batch ZIP associated count");}
  try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(rollZip)){check(zip.size()==2&&zip.getEntry("frame-001.jpg")!=null&&zip.getEntry("index.csv")!=null,"ZIP stable names and index");}
  new CatalogStore(logs).pushPull(roll,2);LogStore.Roll beforePrint=logs.roll(roll);
  File pdf=new File(getTargetContext().getFilesDir(),"contact.pdf");try(OutputStream out=new FileOutputStream(pdf)){check(RollOutput.contactSheet(getTargetContext(),logs,roll,out)==2,"contact includes unmatched exposures");}
  try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(pdf,android.os.ParcelFileDescriptor.MODE_READ_ONLY);android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(fd)){check(renderer.getPageCount()==1,"contact single page");}
  check(logs.roll(roll).iso==beforePrint.iso&&new CatalogStore(logs).detail(roll).pushPull==2,"EI print leaves base ISO and push unchanged");
  String rounding=logs.addRoll("EI rounding fixture","ISO 50 original","Camera",50,36);logs.insert(rounding,40000,-10,480,"denied","","","","","","");new CatalogStore(logs).pushPull(rounding,-2);
  File roundedPdf=new File(getTargetContext().getFilesDir(),"contact-ei-rounding.pdf");try(OutputStream out=new FileOutputStream(roundedPdf)){check(RollOutput.contactSheet(getTargetContext(),logs,rounding,out)==1,"EI rounding PDF");}
  check(logs.roll(rounding).iso==50&&new CatalogStore(logs).detail(rounding).pushPull==-2,"EI round print read only");
  String longText=String.join("",java.util.Collections.nCopies(40,"中文长名称"));String longRoll=logs.addRoll(longText,longText,longText,50,36);logs.insert(longRoll,40000,-10,480,"denied","","","","","","");new CatalogStore(logs).pushPull(longRoll,-2);
  try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"contact-ei-long-header.pdf"))){check(RollOutput.contactSheet(getTargetContext(),logs,longRoll,out)==1,"long header keeps EI suffix in page bounds");}
  String many=logs.addRoll("分页测试","Test","Camera",100,36);for(int i=0;i<13;i++)logs.insert(many,40000+i*1000,-10,480,"denied","","","","","","");
  File pages=new File(getTargetContext().getFilesDir(),"contact-pages.pdf");try(OutputStream out=new FileOutputStream(pages)){check(RollOutput.contactSheet(getTargetContext(),logs,many,out)==13,"contact total frames");}
  try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(pages,android.os.ParcelFileDescriptor.MODE_READ_ONLY);android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(fd)){check(renderer.getPageCount()==2,"contact page boundary");}
  logs.close();
  String csv="id,roll_name,stock,camera,iso,frame_number,tapped_at_utc,captured_at_utc,offset_seconds,zone_offset_minutes,status,memo,roll_id,roll_capacity\r\n"+
     "csv-1,测试卷,Gold,Camera,200,1,2026-10-22T00:00:05Z,2026-10-21T23:59:55Z,-10,480,denied,\"街头,夜景\n第二行 \"\"引号\"\"\",roll-A,36\r\n";
  CsvImport.Preview parsed=CsvImport.parse(new StringReader(csv));check(!parsed.legacy&&parsed.rows==1&&parsed.data.count("rolls")==1,"CSV stable grouping");
  check(parsed.data.manifest.getJSONArray("shots").getJSONObject(0).getString("memo").equals("街头,夜景\n第二行 \"引号\""),"CSV multiline quoted Unicode");
  String previewBefore=parsed.data.manifest.toString();
  for(String badIso:new String[]{"0","25601","1.5","99999999999999"}){try{CsvImport.review(parsed,0,"Gold",badIso,"Camera","120");throw new AssertionError("invalid review ISO accepted");}catch(IllegalArgumentException expected){}check(parsed.data.manifest.toString().equals(previewBefore),"invalid review leaves whole preview unchanged");}
  try{CsvImport.review(parsed,0," ","400","Camera","120");throw new AssertionError("blank review film accepted");}catch(IllegalArgumentException expected){}
  check(parsed.data.manifest.toString().equals(previewBefore),"blank review atomic");
  String originalShots=parsed.data.manifest.getJSONArray("shots").toString(),originalRollId=parsed.data.manifest.getJSONArray("rolls").getJSONObject(0).getString("id");
  CsvImport.review(parsed,0," Gold reviewed ","400"," Camera reviewed ","120");
  org.json.JSONObject reviewed=parsed.data.manifest.getJSONArray("rolls").getJSONObject(0);
  check(reviewed.getString("stock").equals("Gold reviewed")&&reviewed.getInt("iso")==400&&reviewed.getString("camera").equals("Camera reviewed"),"review updates trimmed metadata");
  check(reviewed.getString("id").equals(originalRollId)&&reviewed.getString("name").equals("测试卷")&&parsed.data.manifest.getJSONArray("shots").toString().equals(originalShots),"review preserves IDs name and all exposure fields");
  check(parsed.data.manifest.getJSONArray("roll_details").length()==1&&parsed.data.manifest.getJSONArray("roll_details").getJSONObject(0).getString("format").equals("120")&&CsvImport.ready(parsed),"review format and readiness");
  CsvImport.review(parsed,0,"Gold reviewed","400","Camera reviewed","35 mm");check(parsed.data.manifest.getJSONArray("roll_details").length()==1,"repeat review preserves detail identity");
  logs=new LogStore(getTargetContext());int oldCount=logs.rolls().size();FilmBackup.restore(getTargetContext(),logs,parsed.data);check(logs.rolls().size()==oldCount+1,"CSV import append");logs.close();
  try{CsvRecords.read(new StringReader("a,b\n\"unfinished"));throw new AssertionError("CSV missing quote");}catch(IOException expected){}
  try{CsvImport.parse(new StringReader(csv.replace("-10,480","-20,480")));throw new AssertionError("CSV time mismatch");}catch(IOException expected){}
  logs=new LogStore(getTargetContext());ScanStore queueFiles=new ScanStore(getTargetContext(),logs);String queuedRoll=logs.addRoll("恢复任务","Test","Camera",100,36);String resumedId=java.util.UUID.randomUUID().toString();
  try(InputStream in=new FileInputStream(source)){queueFiles.importJpeg(queuedRoll,"saved-before-crash.jpg",in,resumedId);}
  logs.getWritableDatabase().execSQL("INSERT INTO import_jobs(id,roll_id,uri,name,state,created_at) VALUES (?,?,?,?,?,?)",new Object[]{resumedId,queuedRoll,"content://missing-provider/no-access","saved-before-crash.jpg","running",1});
  ImportQueue.run(getTargetContext(),logs,queuedRoll,false);check(ImportQueue.count(logs,queuedRoll,"'done'")==1&&queueFiles.list(queuedRoll).size()==1,"resume after saved file does not duplicate or require old URI");
  String retryId=java.util.UUID.randomUUID().toString();logs.getWritableDatabase().execSQL("INSERT INTO import_jobs(id,roll_id,uri,name,state,created_at) VALUES (?,?,?,?,?,?)",new Object[]{retryId,queuedRoll,"content://missing-provider/no-access","retry.jpg","queued",2});
  ImportQueue.run(getTargetContext(),logs,queuedRoll,false);check(ImportQueue.count(logs,queuedRoll,"'failed'")==1,"failed job retained");
  logs.getWritableDatabase().execSQL("UPDATE import_jobs SET uri=? WHERE id=?",new Object[]{android.net.Uri.fromFile(source).toString(),retryId});
  File part=new File(getTargetContext().getFilesDir(),"scans/"+retryId+".part");try(FileOutputStream out=new FileOutputStream(part)){out.write(new byte[]{1,2,3});}
  ImportQueue.run(getTargetContext(),logs,queuedRoll,true);check(ImportQueue.count(logs,queuedRoll,"'done'")==2&&queueFiles.list(queuedRoll).size()==2&&!part.exists(),"retry replaces partial file and finishes once");
  ImportQueue.run(getTargetContext(),logs,queuedRoll,true);check(queueFiles.list(queuedRoll).size()==2,"completed queue never duplicates");
  logs.getWritableDatabase().execSQL("UPDATE import_jobs SET state='failed' WHERE id=?",new Object[]{retryId});ImportQueue.dismissFailures(getTargetContext(),logs,queuedRoll);
  check(ImportQueue.count(logs,queuedRoll,"'failed'")==0&&ImportQueue.count(logs,queuedRoll,"'cancelled'")==1,"dismiss failures");
  ImportQueue.run(getTargetContext(),logs,queuedRoll,true);check(queueFiles.list(queuedRoll).size()==2&&ImportQueue.count(logs,queuedRoll,"'cancelled'")==1,"cancel keeps assets and prevents retry");
  File orphan=new File(getTargetContext().getFilesDir(),"scans/"+java.util.UUID.randomUUID()+".jpg");Files.copy(source.toPath(),orphan.toPath());
  File interruptedPart=new File(getTargetContext().getFilesDir(),"scans/"+java.util.UUID.randomUUID()+".part");Files.copy(source.toPath(),interruptedPart.toPath());
  File unrelated=new File(getTargetContext().getFilesDir(),"scans/keep-me.txt");try(FileOutputStream out=new FileOutputStream(unrelated)){out.write(1);}
  queueFiles.remove(queuedRoll,resumedId);FilmApplication.cleanupFiles(getTargetContext(),logs);
  check(!orphan.exists()&&!interruptedPart.exists()&&unrelated.exists()&&queueFiles.file(resumedId).exists()&&queueFiles.file(retryId).exists(),"startup removes only orphan/staged files and retains active/trash scans");unrelated.delete();logs.close();
  File large=new File(getTargetContext().getCacheDir(),"large-progressive.jpg");try(InputStream in=getTargetContext().getAssets().open("large-progressive-icc.jpg");OutputStream out=new FileOutputStream(large)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
  Bitmap small=ScanImages.read(large,512);check(small.getWidth()<=512&&small.getHeight()<=512,"large progressive image sampled before decoding");small.recycle();
  File largeOutput=ScanExport.create(getTargetContext(),large,r,s,false);ExifInterface largeExif=new ExifInterface(largeOutput.toString());check(largeExif.getAttributeInt("Orientation",0)==1,"missing orientation normalized");
  Files.copy(largeOutput.toPath(),new File(getTargetContext().getFilesDir(),"large-export.jpg").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);largeOutput.delete();
  int count=getTargetContext().getCacheDir().list().length;s.lat=91.0;
  try{ScanExport.create(getTargetContext(),source,r,s,true);throw new AssertionError("bad latitude accepted");}catch(IOException expected){}
  check(count==getTargetContext().getCacheDir().list().length,"failed export cleaned");
  try{JpegCopy.clean(new ByteArrayInputStream(Arrays.copyOf(before,before.length-2)),new ByteArrayOutputStream());throw new AssertionError("truncated JPEG accepted");}catch(IOException expected){}
  result.putString("stream","PASS: source protection, identical pixels, orientation, adjusted cross-day timestamp, zone, milliseconds, old XMP removal, GPS on/off and fix time, invalid coordinate cleanup, truncated JPEG rejection; exact scan imports, same-name safety, association swap, duplicate/cross-roll/stale rejection, rollback, recent deletion and conflict-safe restore, replacement, persistence; full data/media archive, append restore, rollback, corruption and path traversal rejection; whole-roll ZIP/index and contact PDF pagination; quoted multiline CSV and time validation; persistent import resume, retry and duplicate guard; 48MP progressive ICC JPEG sampled/exported; startup cleanup and cancelled-job protection; "+(android.os.Build.VERSION.SDK_INT>=29?"album publication, adjusted dates, GPS off, partial rollback and interruption cleanup":"album tests skipped below API29; ZIP fallback")+"\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
 private byte[] archiveWithZone(byte[] bytes,int zone)throws Exception{ByteArrayOutputStream result=new ByteArrayOutputStream();try(java.util.zip.ZipInputStream input=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));java.util.zip.ZipOutputStream output=new java.util.zip.ZipOutputStream(result)){java.util.zip.ZipEntry entry;while((entry=input.getNextEntry())!=null){ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)data.write(buffer,0,n);byte[] payload=data.toByteArray();if(entry.getName().equals("manifest.json")){org.json.JSONObject manifest=new org.json.JSONObject(new String(payload,"UTF-8"));manifest.getJSONArray("shots").getJSONObject(0).put("zone_offset_minutes",zone);payload=manifest.toString().getBytes("UTF-8");}output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));output.write(payload);output.closeEntry();}}return result.toByteArray();}
}
