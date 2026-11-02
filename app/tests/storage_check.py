"""Host SQLite check of the schema and guarded update used by the Android store.
Does not substitute for an Android device test.
"""
import pathlib,re,sqlite3,tempfile
src=(pathlib.Path(__file__).parent.parent/'src/cn/sixnine/filmlog/LogStore.java').read_text()
schema=re.search(r'CREATE_SHOTS="([^"]+)"',src).group(1)
recover=re.search(r'execSQL\("(UPDATE shots [^"]+)"\)',src).group(1)
guard="id=? AND status='pending'"
assert guard in src
with tempfile.TemporaryDirectory() as tmp:
 path=pathlib.Path(tmp)/'records.db'
 db=sqlite3.connect(path);db.execute(schema)
 db.execute("INSERT INTO shots (id,tapped_at,captured_at,offset_seconds,zone_offset_minutes,status) VALUES (?,?,?,?,?,?)",('one',20000,10000,-10,480,'pending'))
 db.commit();db.close();db=sqlite3.connect(path)
 assert db.execute('SELECT captured_at,offset_seconds FROM shots').fetchone()==(10000,-10)
 db.execute('UPDATE shots SET status=?,latitude=?,longitude=? WHERE '+guard,('acquired',31.,121.,'one'))
 db.execute('UPDATE shots SET status=? WHERE '+guard,('timeout','one'))
 assert db.execute('SELECT status,latitude,longitude FROM shots').fetchone()==('acquired',31.,121.)
 try:
  db.execute("INSERT INTO shots (id,tapped_at,captured_at,offset_seconds,zone_offset_minutes,status) VALUES ('one',1,1,0,0,'pending')")
  raise AssertionError('duplicate ID was accepted')
 except sqlite3.IntegrityError:pass
 db.execute("INSERT INTO shots (id,tapped_at,captured_at,offset_seconds,zone_offset_minutes,status) VALUES ('two',2,2,0,0,'pending')")
 db.execute(recover)
 assert db.execute("SELECT status,latitude,longitude FROM shots WHERE id='two'").fetchone()==('interrupted',None,None)
 assert db.execute("SELECT status FROM shots WHERE id='one'").fetchone()==('acquired',)
 db.close()
print('SQLite: persistence, duplicate ID, terminal-state guard and interrupted recovery passed')
