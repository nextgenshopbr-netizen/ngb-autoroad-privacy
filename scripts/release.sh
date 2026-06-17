#!/bin/bash
#
# NGBAutoRoad - Script de Release
# Uso: ./scripts/release.sh <versão>
# Exemplo: ./scripts/release.sh 3.1.0
#
# Este script:
# 1. Atualiza versionName e versionCode no build.gradle.kts
# 2. Faz commit das alterações
# 3. Cria a tag de versão
# 4. Faz push (dispara CI/CD automaticamente)
#

set -e

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Verificar argumento
if [ -z "$1" ]; then
    echo -e "${RED}Erro: Informe a versão.${NC}"
    echo ""
    echo "Uso: ./scripts/release.sh <versão>"
    echo "Exemplo: ./scripts/release.sh 3.1.0"
    echo ""
    echo "Versões válidas: X.Y.Z (ex: 3.0.1, 3.1.0, 4.0.0)"
    exit 1
fi

VERSION=$1
TAG="v${VERSION}"

# Validar formato da versão
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+)?$ ]]; then
    echo -e "${RED}Erro: Formato de versão inválido.${NC}"
    echo "Use o formato: X.Y.Z ou X.Y.Z-beta"
    exit 1
fi

# Calcular versionCode (major*10000 + minor*100 + patch)
IFS='.' read -r MAJOR MINOR PATCH <<< "${VERSION%%-*}"
VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))

echo -e "${YELLOW}═══════════════════════════════════════${NC}"
echo -e "${YELLOW}  NGBAutoRoad - Nova Release${NC}"
echo -e "${YELLOW}═══════════════════════════════════════${NC}"
echo ""
echo -e "  Versão:      ${GREEN}${VERSION}${NC}"
echo -e "  Tag:         ${GREEN}${TAG}${NC}"
echo -e "  VersionCode: ${GREEN}${VERSION_CODE}${NC}"
echo ""

# Verificar se estamos na branch main
BRANCH=$(git branch --show-current)
if [ "$BRANCH" != "main" ]; then
    echo -e "${YELLOW}⚠️  Você está na branch '${BRANCH}', não na 'main'.${NC}"
    read -p "Continuar mesmo assim? (s/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Ss]$ ]]; then
        exit 1
    fi
fi

# Verificar se há alterações não commitadas
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo -e "${YELLOW}⚠️  Há alterações não commitadas.${NC}"
    read -p "Fazer commit de tudo antes de criar a release? (S/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Nn]$ ]]; then
        git add -A
        git commit -m "chore: preparar release ${TAG}"
    fi
fi

# Verificar se a tag já existe
if git tag -l "$TAG" | grep -q "$TAG"; then
    echo -e "${RED}Erro: A tag ${TAG} já existe.${NC}"
    exit 1
fi

# Atualizar versionName e versionCode no build.gradle.kts
echo -e "${GREEN}📝 Atualizando versão no build.gradle.kts...${NC}"
sed -i "s/versionCode = [0-9]*/versionCode = ${VERSION_CODE}/" app/build.gradle.kts
sed -i "s/versionName = \"[^\"]*\"/versionName = \"${VERSION}\"/" app/build.gradle.kts

# Verificar alteração
echo "  versionCode = ${VERSION_CODE}"
echo "  versionName = \"${VERSION}\""

# Commit da versão
git add app/build.gradle.kts
git commit -m "release: ${TAG}

- versionName: ${VERSION}
- versionCode: ${VERSION_CODE}"

# Criar tag
echo -e "${GREEN}🏷️  Criando tag ${TAG}...${NC}"
git tag -a "$TAG" -m "Release ${TAG}"

# Push
echo -e "${GREEN}🚀 Fazendo push...${NC}"
git push origin "$BRANCH"
git push origin "$TAG"

echo ""
echo -e "${GREEN}═══════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ Release ${TAG} criada com sucesso!${NC}"
echo -e "${GREEN}═══════════════════════════════════════${NC}"
echo ""
echo "O GitHub Actions vai:"
echo "  1. Compilar o APK automaticamente"
echo "  2. Criar a Release no GitHub com o APK"
echo "  3. Gerar o changelog automático"
echo ""
echo "Acompanhe em:"
echo "  https://github.com/nextgenshopbr-netizen/ngb-autoroad-privacy/actions"
echo ""
