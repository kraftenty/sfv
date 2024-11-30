# SFV
### Super Fast Version-Control-System
> 병렬 처리 기법을 도입하여 대용량 리포지토리 버전 관리 퍼포먼스를 향상시킨 버전 관리 시스템


* usage
```
$ sfv init
$ sfv commit -m "commit message"
$ sfv status
$ stv log
$ sfv checkout [target commit ID]
```

* performance result
> 실험군 : 기존 버전 관리 시스템(Git), 본 버전 관리 시스템(SFV)  
> 대조군 : 
> * mysql-server 약 1.24 GB (2389 directories, 48173 files)  
>   https://github.com/mysql/mysql-server
> * tensorflow - 약 409 MB (2229 directories, 34118 files)  
>   https://github.com/tensorflow/tensorflow

실험결과 : 
| 작업 | Git | SFV |
|------|-----|-----|
| Commit (91천) | 17.05초 | 6.39초 |
| Checkout (4천) | 5.77초 | 5.82초 |

리포트 : 
https://docs.google.com/document/d/1P1Ra6iWN1d6sKSznhIb1Xjiik4zfbMjt/edit?usp=sharing&ouid=113517447782812227966&rtpof=true&sd=true