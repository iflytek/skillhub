# Dependency-free, bounded JSON syntax/shape check using the image's BusyBox awk.
# Never prints input. Output is a fixed status/field, consumed by 40-analytics.sh.
# Tree nodes use numeric IDs, so hostile keys cannot forge a configuration path.
function bad(field) { print "invalid " field; exit 1 }
function ws() { while (substr(s,p,1) ~ /^[ \t\r\n]$/ && p<=length(s)) p++ }
function string(    out,c,e,h,n,i,d) {
 if (substr(s,p++,1)!="\"") bad("config")
 out=""
 while(p<=length(s)) {
  c=substr(s,p++,1)
  if(c=="\"") return out
  if(c ~ /[[:cntrl:]]/) bad("config")
  if(c=="\\") {
   e=substr(s,p++,1)
   if(e=="u") {
    h=substr(s,p,4); p+=4
    if(length(h)!=4 || h ~ /[^0-9a-fA-F]/) bad("config")
    n=0
    for(i=1;i<=4;i++) { d=index("0123456789abcdef",tolower(substr(h,i,1)))-1; n=n*16+d }
    # ASCII escapes must compare exactly to ordinary keys/UUIDs. Non-ASCII
    # is valid JSON but cannot match any deployment control field/value.
    c=(n>0 && n<128) ? sprintf("%c",n) : "\\u" h
   } else if(e=="\"" || e=="\\" || e=="/") c=e
   else if(e=="b") c=sprintf("%c",8)
   else if(e=="f") c=sprintf("%c",12)
   else if(e=="n") c="\n"
   else if(e=="r") c="\r"
   else if(e=="t") c="\t"
   else bad("config")
  }
  out=out c
 }
 bad("config")
}
function value(depth,    id,c,key,item,token) {
 if(depth>32) bad("config")
 ws(); id=++nodes; c=substr(s,p,1)
 if(c=="{") {
  kind[id]="object"; p++; ws()
  if(substr(s,p,1)=="}") { p++; return id }
  while(1) {
   ws(); key=string(); ws()
   if(substr(s,p++,1)!=":") bad("config")
   if((id SUBSEP key) in child) bad("config")
   item=value(depth+1); child[id SUBSEP key]=item; count[id]++
   ws(); c=substr(s,p++,1)
   if(c=="}") return id
   if(c!=",") bad("config")
  }
 } else if(c=="[") {
  kind[id]="array"; p++; ws()
  if(substr(s,p,1)=="]") { p++; return id }
  while(1) {
   item=value(depth+1); ws(); c=substr(s,p++,1)
   if(c=="]") return id
   if(c!=",") bad("config")
  }
 } else if(c=="\"") { kind[id]="string"; data[id]=string(); return id }
 else {
  token=substr(s,p)
  if(match(token,/^-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?/)) { kind[id]="number"; p+=RLENGTH; return id }
  if(substr(token,1,4)=="true") { kind[id]="boolean"; data[id]="true"; p+=4; return id }
  if(substr(token,1,5)=="false") { kind[id]="boolean"; data[id]="false"; p+=5; return id }
  if(substr(token,1,4)=="null") { kind[id]="null"; p+=4; return id }
  bad("config")
 }
}
{ s=s $0 "\n" }
END {
 if(length(s)>65537) bad("config")
 p=1; root=value(0); ws()
 if(p<=length(s) || kind[root]!="object") bad("config")
 for(k in child) if(index(k,root SUBSEP)==1) {
  name=substr(k,length(root SUBSEP)+1)
  if(name!="enabled" && name!="consent" && name!="adapter" && name!="rules" && name!="adapterConfig") bad("config")
 }
 enabled=child[root SUBSEP "enabled"]; consent=child[root SUBSEP "consent"]
 adapter=child[root SUBSEP "adapter"]; settings=child[root SUBSEP "adapterConfig"]
 if(enabled && kind[enabled]!="boolean") bad("enabled")
 if(consent && (kind[consent]!="string" || (data[consent]!="denied" && data[consent]!="granted"))) bad("consent")
 if(adapter && (kind[adapter]!="string" || data[adapter]!="umami")) bad("adapter")
 if(settings && kind[settings]!="object") bad("adapterConfig")
 if(data[enabled]=="true") {
  if(data[adapter]!="umami") bad("adapter")
  website=child[settings SUBSEP "websiteId"]; uuid=data[website]
  if(kind[settings]!="object" || count[settings]!=1 || kind[website]!="string" || length(uuid)!=36 || uuid !~ /^[0-9a-fA-F-]+$/ || substr(uuid,9,1)!="-" || substr(uuid,14,1)!="-" || substr(uuid,19,1)!="-" || substr(uuid,24,1)!="-") bad("adapterConfig")
  gsub(/-/,"",uuid); if(length(uuid)!=32) bad("adapterConfig")
  # Denied pages can later call start(), so retain valid public configuration.
  print (data[consent]=="granted" ? "enabled" : "denied")
 } else print "disabled"
}
