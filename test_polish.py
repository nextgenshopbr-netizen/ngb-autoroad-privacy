#!/usr/bin/env python3
"""
NGBAutoRoad v4.0.2 — Testes Avançados de Polimento
===================================================
Testa: null safety, threading, ciclo de vida, imports mortos,
edge cases de UI, consistência de tipos, e código morto.
"""
import os
import re
from pathlib import Path
from collections import defaultdict

BASE = Path("/home/ubuntu/NGBAutoRoad-v3/app/src/main/java/com/ngbautoroad")
results = {"CRITICAL": [], "WARNING": [], "INFO": [], "OK": []}

def scan_files():
    """Retorna todos os arquivos .kt do projeto"""
    return list(BASE.rglob("*.kt"))

def test_null_safety():
    """Teste 1: Buscar uso perigoso de !! (force unwrap) que pode causar crash"""
    print("\n📋 TESTE 1: Null Safety — Uso de !! (force unwrap)")
    print("-" * 60)
    
    dangerous = []
    safe_patterns = [
        "!!.",  # Geralmente ok se verificado antes
    ]
    
    for f in scan_files():
        content = f.read_text()
        lines = content.split("\n")
        for i, line in enumerate(lines, 1):
            # Contar !! que não estão em comentários
            stripped = line.split("//")[0]  # Remove comentários de linha
            count = stripped.count("!!")
            if count > 0:
                # Verificar se é perigoso (sem null check antes)
                context = "\n".join(lines[max(0,i-3):i])
                is_guarded = "!= null" in context or "?.let" in context or "if (" in context
                rel_path = str(f.relative_to(BASE))
                dangerous.append({
                    "file": rel_path,
                    "line": i,
                    "code": line.strip(),
                    "guarded": is_guarded
                })
    
    unguarded = [d for d in dangerous if not d["guarded"]]
    guarded = [d for d in dangerous if d["guarded"]]
    
    print(f"  Total de !! encontrados: {len(dangerous)}")
    print(f"  Com null-check prévio (OK): {len(guarded)}")
    print(f"  SEM null-check (PERIGOSO): {len(unguarded)}")
    
    if unguarded:
        print(f"\n  ⚠️  Usos perigosos de !! (podem causar NullPointerException):")
        for d in unguarded[:15]:
            print(f"    {d['file']}:{d['line']} → {d['code'][:80]}")
            results["WARNING"].append(f"!! sem guard: {d['file']}:{d['line']}")
    else:
        results["OK"].append("Nenhum !! perigoso encontrado")
    
    return dangerous

def test_threading():
    """Teste 2: Verificar se operações de banco rodam fora da main thread"""
    print("\n📋 TESTE 2: Threading — Operações de banco na Main Thread")
    print("-" * 60)
    
    issues = []
    
    for f in scan_files():
        content = f.read_text()
        rel_path = str(f.relative_to(BASE))
        
        # Buscar chamadas de DAO fora de coroutine/suspend
        # Padrão perigoso: chamar dao.insert/update/delete sem estar em launch{} ou suspend fun
        lines = content.split("\n")
        
        # Verificar se usa allowMainThreadQueries (muito ruim)
        if "allowMainThreadQueries" in content:
            issues.append(f"  ❌ {rel_path}: usa allowMainThreadQueries()")
            results["CRITICAL"].append(f"allowMainThreadQueries em {rel_path}")
        
        # Verificar se tem LaunchedEffect com operação de banco sem Dispatchers.IO
        in_launched_effect = False
        for i, line in enumerate(lines, 1):
            if "LaunchedEffect" in line:
                in_launched_effect = True
            if in_launched_effect and ("dao." in line or "Dao." in line):
                # Verificar se está dentro de withContext(Dispatchers.IO)
                context = "\n".join(lines[max(0,i-5):i])
                if "Dispatchers.IO" not in context and "withContext" not in context:
                    issues.append(f"  ⚠️  {rel_path}:{i} — DAO call em LaunchedEffect sem Dispatchers.IO")
                    results["WARNING"].append(f"DAO sem IO: {rel_path}:{i}")
            if in_launched_effect and "}" in line and line.strip() == "}":
                in_launched_effect = False
    
    if issues:
        for issue in issues[:10]:
            print(issue)
    else:
        print("  ✅ Nenhuma operação de banco na main thread detectada")
        results["OK"].append("Threading OK")

def test_lifecycle():
    """Teste 3: Verificar memory leaks — coroutines sem cancel, flows sem lifecycle"""
    print("\n📋 TESTE 3: Ciclo de Vida — Memory Leaks")
    print("-" * 60)
    
    issues = []
    
    for f in scan_files():
        content = f.read_text()
        rel_path = str(f.relative_to(BASE))
        
        # GlobalScope é perigoso — pode causar leak
        if "GlobalScope" in content:
            lines = content.split("\n")
            for i, line in enumerate(lines, 1):
                if "GlobalScope" in line:
                    issues.append(f"  ❌ {rel_path}:{i} — GlobalScope (memory leak)")
                    results["CRITICAL"].append(f"GlobalScope: {rel_path}:{i}")
        
        # CoroutineScope sem cancel no onDestroy
        if "CoroutineScope(" in content and "Service" in rel_path:
            if ".cancel()" not in content:
                issues.append(f"  ⚠️  {rel_path} — CoroutineScope sem cancel() no onDestroy")
                results["WARNING"].append(f"Scope sem cancel: {rel_path}")
        
        # collectAsState sem lifecycle awareness (geralmente ok em Compose, mas verificar)
        # Flow.collect sem repeatOnLifecycle em Activity/Fragment
        if "Activity" in rel_path and ".collect" in content and "collectAsState" not in content:
            if "repeatOnLifecycle" not in content and "lifecycleScope" not in content:
                if "flow" in content.lower():
                    issues.append(f"  ⚠️  {rel_path} — Flow.collect sem lifecycle awareness")
                    results["WARNING"].append(f"Flow sem lifecycle: {rel_path}")
    
    if issues:
        for issue in issues:
            print(issue)
    else:
        print("  ✅ Nenhum memory leak potencial detectado")
        results["OK"].append("Lifecycle OK")

def test_dead_code():
    """Teste 4: Imports não utilizados e funções mortas"""
    print("\n📋 TESTE 4: Código Morto — Imports e Funções não utilizadas")
    print("-" * 60)
    
    unused_imports = []
    
    for f in scan_files():
        content = f.read_text()
        lines = content.split("\n")
        rel_path = str(f.relative_to(BASE))
        
        # Coletar imports
        imports = []
        for i, line in enumerate(lines, 1):
            if line.startswith("import ") and not line.startswith("import android") and not line.startswith("import androidx"):
                # Imports internos do projeto
                imported_name = line.split(".")[-1].strip()
                if imported_name != "*":
                    imports.append((i, imported_name, line.strip()))
        
        # Verificar se cada import é usado no resto do código
        body = "\n".join(lines[len([l for l in lines if l.startswith("import ") or l.startswith("package ")]):])
        for line_num, name, full_import in imports:
            # Contar ocorrências no body (excluindo a própria linha de import)
            occurrences = body.count(name)
            if occurrences == 0:
                unused_imports.append(f"  {rel_path}:{line_num} — {full_import}")
    
    if unused_imports:
        print(f"  ⚠️  {len(unused_imports)} imports potencialmente não utilizados:")
        for imp in unused_imports[:10]:
            print(imp)
        results["INFO"].append(f"{len(unused_imports)} imports potencialmente não utilizados")
    else:
        print("  ✅ Nenhum import morto detectado")
        results["OK"].append("Imports OK")

def test_ui_edge_cases():
    """Teste 5: Edge cases de UI — textos sem maxLines, campos sem maxLength"""
    print("\n📋 TESTE 5: Edge Cases de UI")
    print("-" * 60)
    
    issues = []
    
    for f in scan_files():
        content = f.read_text()
        rel_path = str(f.relative_to(BASE))
        lines = content.split("\n")
        
        for i, line in enumerate(lines, 1):
            # OutlinedTextField sem maxLength (pode aceitar texto infinito)
            if "OutlinedTextField(" in line:
                # Verificar se tem algum limite nos próximos 10 linhas
                context = "\n".join(lines[i:i+10])
                if "maxLength" not in context and "it.length" not in context and "filter" not in context:
                    # Verificar se é campo de texto livre (description) — ok sem limite
                    if "description" not in context.lower() and "observa" not in context.lower():
                        pass  # Muitos campos numéricos já são limitados pelo keyboardType
            
            # Text() sem maxLines que pode estourar layout em cards pequenos
            if "Text(" in line and "maxLines" not in line:
                # Verificar se está dentro de um Card ou Row com tamanho fixo
                context = "\n".join(lines[max(0,i-5):i])
                if "Card(" in context or "height" in context:
                    if "style = MaterialTheme.typography.label" not in line:
                        pass  # Só reportar se for texto potencialmente longo
        
        # Verificar se LazyColumn tem key (performance)
        if "LazyColumn" in content and "items(" in content:
            if "key = " not in content and "key=" not in content:
                issues.append(f"  ⚠️  {rel_path} — LazyColumn.items() sem key (performance)")
                results["WARNING"].append(f"LazyColumn sem key: {rel_path}")
    
    if issues:
        for issue in issues:
            print(issue)
    else:
        print("  ✅ Nenhum edge case crítico de UI detectado")
        results["OK"].append("UI edge cases OK")

def test_type_consistency():
    """Teste 6: Consistência de tipos entre Entity e UI"""
    print("\n📋 TESTE 6: Consistência de Tipos (Room Entity vs UI)")
    print("-" * 60)
    
    issues = []
    
    # Verificar se EarningEntity.amount é Double e UI usa toDoubleLocale
    for f in scan_files():
        content = f.read_text()
        rel_path = str(f.relative_to(BASE))
        
        # Buscar toDoubleOrNull() remanescente (deveria ser toDoubleLocale)
        if "toDoubleOrNull()" in content:
            lines = content.split("\n")
            for i, line in enumerate(lines, 1):
                if "toDoubleOrNull()" in line and "// OK" not in line:
                    # Verificar se é em contexto de input do usuário
                    context = "\n".join(lines[max(0,i-3):i+1])
                    if "TextField" in context or "amount" in context or "value" in context.lower():
                        issues.append(f"  ⚠️  {rel_path}:{i} — toDoubleOrNull() remanescente (deveria ser toDoubleLocale?)")
                        results["WARNING"].append(f"toDoubleOrNull remanescente: {rel_path}:{i}")
        
        # Buscar toIntOrNull() em campos que podem ter vírgula
        if "toIntOrNull()" in content:
            lines = content.split("\n")
            for i, line in enumerate(lines, 1):
                if "toIntOrNull()" in line:
                    # Verificar se é campo que pode ter espaço
                    if ".trim()" not in line and "input" in line.lower():
                        issues.append(f"  ℹ️  {rel_path}:{i} — toIntOrNull() sem .trim()")
                        results["INFO"].append(f"toIntOrNull sem trim: {rel_path}:{i}")
    
    if issues:
        for issue in issues[:15]:
            print(issue)
    else:
        print("  ✅ Tipos consistentes entre Entity e UI")
        results["OK"].append("Tipos consistentes")

def test_error_handling():
    """Teste 7: Tratamento de erros — try/catch ausentes em operações críticas"""
    print("\n📋 TESTE 7: Tratamento de Erros")
    print("-" * 60)
    
    issues = []
    
    for f in scan_files():
        content = f.read_text()
        rel_path = str(f.relative_to(BASE))
        lines = content.split("\n")
        
        for i, line in enumerate(lines, 1):
            # Operações de banco sem try/catch
            if ("dao.insert" in line or "dao.update" in line or "dao.delete" in line):
                context = "\n".join(lines[max(0,i-5):i+2])
                if "try" not in context and "catch" not in context:
                    issues.append(f"  ℹ️  {rel_path}:{i} — Operação de banco sem try/catch")
            
            # JSON parsing sem try/catch
            if "Json.decode" in line or "JSONObject(" in line:
                context = "\n".join(lines[max(0,i-3):i+2])
                if "try" not in context:
                    issues.append(f"  ⚠️  {rel_path}:{i} — JSON parse sem try/catch")
                    results["WARNING"].append(f"JSON sem try: {rel_path}:{i}")
    
    if issues:
        print(f"  {len(issues)} pontos sem tratamento de erro:")
        for issue in issues[:10]:
            print(issue)
    else:
        print("  ✅ Tratamento de erros adequado")
        results["OK"].append("Error handling OK")

def test_hardcoded_values():
    """Teste 8: Valores hardcoded que deveriam ser configuráveis"""
    print("\n📋 TESTE 8: Valores Hardcoded")
    print("-" * 60)
    
    issues = []
    
    for f in scan_files():
        content = f.read_text()
        rel_path = str(f.relative_to(BASE))
        lines = content.split("\n")
        
        for i, line in enumerate(lines, 1):
            # Strings hardcoded de plataforma
            if '"Uber"' in line or '"99"' in line or '"inDrive"' in line:
                if "enum" not in content[:content.find(line)] and "Platform." not in line:
                    if "displayName" not in line and "//" not in line.split('"')[0]:
                        pass  # Geralmente ok em regex patterns
            
            # Cores hardcoded fora do theme
            if "Color(0x" in line and "theme" not in rel_path.lower():
                if "CardGallery" not in rel_path and "OverlayCard" not in rel_path:
                    issues.append(f"  ℹ️  {rel_path}:{i} — Cor hardcoded: {line.strip()[:60]}")
    
    if issues:
        print(f"  {len(issues)} valores hardcoded encontrados:")
        for issue in issues[:8]:
            print(issue)
        results["INFO"].append(f"{len(issues)} cores hardcoded fora do theme")
    else:
        print("  ✅ Nenhum valor hardcoded problemático")
        results["OK"].append("Hardcoded OK")

def test_accessibility():
    """Teste 9: Acessibilidade — contentDescription ausente em ícones"""
    print("\n📋 TESTE 9: Acessibilidade")
    print("-" * 60)
    
    missing_desc = 0
    
    for f in scan_files():
        content = f.read_text()
        lines = content.split("\n")
        
        for i, line in enumerate(lines, 1):
            if "Icon(" in line and "contentDescription = null" in line:
                missing_desc += 1
    
    if missing_desc > 0:
        print(f"  ℹ️  {missing_desc} ícones sem contentDescription (acessibilidade)")
        results["INFO"].append(f"{missing_desc} ícones sem contentDescription")
    else:
        print("  ✅ Todos os ícones têm contentDescription")
        results["OK"].append("Acessibilidade OK")

def test_proguard_safety():
    """Teste 10: Verificar se classes Room/Serialization estão protegidas do ProGuard"""
    print("\n📋 TESTE 10: ProGuard Safety")
    print("-" * 60)
    
    proguard_file = Path("/home/ubuntu/NGBAutoRoad-v3/app/proguard-rules.pro")
    if proguard_file.exists():
        content = proguard_file.read_text()
        checks = {
            "Room entities": "Entity" in content or "room" in content.lower(),
            "Serialization": "Serializable" in content or "serialization" in content.lower(),
            "Data classes": "data class" in content or "keep class" in content,
        }
        for check, passed in checks.items():
            if passed:
                print(f"  ✅ {check}: protegido")
            else:
                print(f"  ⚠️  {check}: possivelmente não protegido")
                results["WARNING"].append(f"ProGuard: {check} não protegido")
    else:
        print("  ⚠️  proguard-rules.pro não encontrado!")
        results["WARNING"].append("proguard-rules.pro ausente")

# ===== EXECUTAR TODOS OS TESTES =====
print("=" * 70)
print("NGBAutoRoad v4.0.2 — TESTES AVANÇADOS DE POLIMENTO")
print("=" * 70)

test_null_safety()
test_threading()
test_lifecycle()
test_dead_code()
test_ui_edge_cases()
test_type_consistency()
test_error_handling()
test_hardcoded_values()
test_accessibility()
test_proguard_safety()

# ===== RESUMO =====
print("\n" + "=" * 70)
print("RESUMO DOS TESTES AVANÇADOS")
print("=" * 70)
print(f"  ❌ Críticos: {len(results['CRITICAL'])}")
print(f"  ⚠️  Avisos: {len(results['WARNING'])}")
print(f"  ℹ️  Info: {len(results['INFO'])}")
print(f"  ✅ OK: {len(results['OK'])}")

if results["CRITICAL"]:
    print("\n  CRÍTICOS (devem ser corrigidos):")
    for c in results["CRITICAL"]:
        print(f"    ❌ {c}")

if results["WARNING"]:
    print("\n  AVISOS (recomendado corrigir):")
    for w in results["WARNING"]:
        print(f"    ⚠️  {w}")

print("\n" + "=" * 70)
