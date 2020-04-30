source $VIMRUNTIME/defaults.vim
packadd! matchit
set packpath^=~/.vim
packadd minpac
call minpac#init()
call minpac#add('k-takata/minpac', {'type': 'opt'})
call minpac#add('junegunn/fzf')
call minpac#add('junegunn/fzf.vim')
" On-demand loading
call minpac#add('aklt/plantuml-syntax',{'type': 'opt'})
call minpac#add('tpope/vim-fugitive',{'type': 'opt'})
call minpac#add('neoclide/coc.nvim',{'type': 'opt', 'branch': 'release'})
"call minpac#add('vim-airline/vim-airline')
call minpac#add('majutsushi/tagbar')
call minpac#add('mileszs/ack.vim')
call minpac#add('easymotion/vim-easymotion')
"call minpac#add('ycm-core/YouCompleteMe')
call minpac#add('will133/vim-dirdiff')
packadd vim-fugitive

syntax on " Enable syntax highlighting.
filetype plugin indent on " Enable file type based indentation.
set nu
set smarttab
" set cursorline
set hlsearch
set nocompatible
" set nowrapscan
set ignorecase " do case insensitive search and tag search
set smartcase " switch to case sensitive automatically when upper case in pattern
set wildignorecase " ignore case when open file by wildcard
set autoindent " Respect indentation when starting a new line.
set expandtab " Expand tabs to spaces. Essential in Python.
set tabstop=4 " Number of spaces tab is counted for.
set shiftwidth=4 " Number of spaces to use for autoindent.
set backspace=2 " Fix backspace behavior on most terminals.
set tags=.git/tags;
set clipboard=unnamed,unnamedplus
set wildignore+=*/node_modules/*
set wildignore+=*/target/*
"colorscheme industry " Change a colorscheme.
set directory-=. "don't creat swap file in the same directory of the editting file, the default value of diretory as: directory=.,~/tmp,/var/tmp,/tmp
set linebreak "break by word not by character

if &diff
    " diff mode"
    set lines=999 columns=999
    set diffopt+=iwhite
    set mouse=a
    map ] ]c
    map [ [c
    nnoremap w 3w
    nnoremap b 3b
    set nocursorline
    syntax off
    set diffexpr=DiffW()
    function DiffW()
        let opt = ""
        if &diffopt =~ "icase"
            let opt = opt . "-i "
        endif
        if &diffopt =~ "iwhite"
            let opt = opt . "-w " " swapped vim's -b with -w
        endif
        silent execute "!diff -a --binary " . opt .
                    \ v:fname_in . " " . v:fname_new .  " > " . v:fname_out
    endfunction
endif

" remap the ]f(go to file) command
nnoremap ]f :e **/src/**/<C-r><C-w>

if executable('ag')
  let g:ackprg = 'ag --vimgrep'
endif

cnoreabbrev Ack Ack!
nnoremap <Leader>s :Ack!<Space>
nnoremap <Leader>a :Ag<CR>
nnoremap <Leader>r :Rg<CR>
nnoremap <Leader>t :Tags<CR>
nnoremap <Leader>f :<C-u>FZF<CR>
nnoremap <Leader>n :set nu!<CR>

augroup MarkdownSpecific 
  au!
  autocmd BufEnter * if &filetype == "" | setlocal ft=markdown | set nonu | else | set nu | endif
  autocmd BufReadPost * if &filetype == "markdown" | set nonu | else | set nu | endif
augroup END

" file is large from 10mb
let g:LargeFile = 1024 * 1024 * 10
augroup LargeFile 
  au!
  autocmd BufReadPre * let f=getfsize(expand("<afile>")) | if f > g:LargeFile || f == -2 | call LargeFile() | else | call NormalFile() | endif
augroup END

function! LargeFile()
 " no syntax highlighting etc
 set eventignore+=FileType
 " not do incsearch
 set noincsearch
 " save memory when other file is viewed
 setlocal bufhidden=unload
 " is read-only (write with :w new_filename)
 setlocal buftype=nowrite
 " no undo possible
 setlocal undolevels=-1
 " display message
 autocmd VimEnter *  echo "The file is larger than " . (g:LargeFile / 1024 / 1024) . " MB, so some options are changed (see .vimrc for details)."
endfunction

function! NormalFile()
 " syntax highlighting etc
 set eventignore-=FileType
 " do incsearch
 set incsearch
endfunction
