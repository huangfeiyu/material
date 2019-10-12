call plug#begin()

" On-demand loading
Plug 'aklt/plantuml-syntax'
call plug#end()
if &diff
    " diff mode"
    set diffopt+=iwhite
    set mouse=a
    map ] ]c
    map [ [c
endif
set nu
set tabstop=4
set shiftwidth=4
set smarttab
set autoindent
set expandtab
set cursorline
set hlsearch
set nocompatible
set nowrapscan
set clipboard=unnamedplus
set wildignore+=*/node_modules/*
