//
//! Copyright 2020 Alibaba Group Holding Limited.
//!
//! Licensed under the Apache License, Version 2.0 (the "License");
//! you may not use this file except in compliance with the License.
//! You may obtain a copy of the License at
//!
//! http://www.apache.org/licenses/LICENSE-2.0
//!
//! Unless required by applicable law or agreed to in writing, software
//! distributed under the License is distributed on an "AS IS" BASIS,
//! WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//! See the License for the specific language governing permissions and
//! limitations under the License.

use crate::structure::{Element, GraphElement, Tag};
use crate::Object;
use bit_set::BitSet;
use pegasus_common::codec::{Decode, Encode};
use pegasus_common::downcast::*;
use pegasus_common::io::{ReadExt, WriteExt};
use std::cell::RefCell;
use std::collections::HashSet;
use std::fmt::Debug;
use std::io;
use std::ops::Deref;
use vec_map::VecMap;

#[derive(Clone)]
pub enum PathItem {
    OnGraph(GraphElement),
    Detached(Object),
    Empty,
}

impl Debug for PathItem {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            PathItem::OnGraph(e) => write!(f, "{:?}", e),
            PathItem::Detached(e) => write!(f, "{:?}", e),
            PathItem::Empty => write!(f, ""),
        }
    }
}

impl Encode for PathItem {
    fn write_to<W: WriteExt>(&self, writer: &mut W) -> io::Result<()> {
        match self {
            PathItem::OnGraph(e) => {
                writer.write_u8(0)?;
                e.write_to(writer)?;
            }
            PathItem::Detached(obj) => {
                writer.write_u8(1)?;
                obj.write_to(writer)?;
            }
            PathItem::Empty => {
                writer.write_u8(2)?;
            }
        }
        Ok(())
    }
}

impl Decode for PathItem {
    fn read_from<R: ReadExt>(reader: &mut R) -> io::Result<Self> {
        let e = reader.read_u8()?;
        match e {
            0 => {
                let ele = <GraphElement>::read_from(reader)?;
                Ok(PathItem::OnGraph(ele))
            }
            1 => {
                let obj = <Object>::read_from(reader)?;
                Ok(PathItem::Detached(obj))
            }
            2 => Ok(PathItem::Empty),
            _ => Err(io::Error::new(io::ErrorKind::Other, "unreachable")),
        }
    }
}

impl PathItem {
    #[inline]
    pub fn as_element(&self) -> Option<&GraphElement> {
        match self {
            PathItem::OnGraph(e) => Some(e),
            PathItem::Detached(_) => None,
            PathItem::Empty => None,
        }
    }

    #[inline]
    pub fn as_mut_element(&mut self) -> Option<&mut GraphElement> {
        match self {
            PathItem::OnGraph(e) => Some(e),
            PathItem::Detached(_) => None,
            PathItem::Empty => None,
        }
    }

    #[inline]
    pub fn as_detached(&self) -> Option<&Object> {
        match self {
            PathItem::OnGraph(_) => None,
            PathItem::Detached(e) => Some(e),
            PathItem::Empty => None,
        }
    }

    #[inline]
    pub fn as_mut_detached(&mut self) -> Option<&mut Object> {
        match self {
            PathItem::OnGraph(_) => None,
            PathItem::Detached(e) => Some(e),
            PathItem::Empty => None,
        }
    }
}

impl From<GraphElement> for PathItem {
    fn from(e: GraphElement) -> Self {
        PathItem::OnGraph(e)
    }
}

impl From<Object> for PathItem {
    fn from(o: Object) -> Self {
        PathItem::Detached(o)
    }
}

#[derive(Clone, Debug)]
pub enum PathHead {
    Item(PathItem),
    Index(usize),
}

#[derive(Clone)]
pub struct Path {
    history: Vec<PathItem>,
    head: PathHead,
    tags: RefCell<VecMap<usize>>,
}

impl Path {
    pub fn new<T: Into<GraphElement>>(first: T, is_label_path: bool) -> Self {
        let first = PathItem::OnGraph(first.into());
        if is_label_path {
            Path { history: vec![], head: PathHead::Item(first), tags: RefCell::new(VecMap::new()) }
        } else {
            Path {
                history: vec![first],
                head: PathHead::Index(0),
                tags: RefCell::new(VecMap::new()),
            }
        }
    }

    pub fn size(&self) -> usize {
        self.history.len()
    }

    pub fn head(&self) -> &PathItem {
        match &self.head {
            PathHead::Item(item) => item,
            PathHead::Index(index) => {
                assert!(*index < self.history.len());
                &self.history[*index]
            }
        }
    }

    pub fn head_mut(&mut self) -> &mut PathItem {
        match &mut self.head {
            PathHead::Item(item) => item,
            PathHead::Index(index) => {
                assert!(*index < self.history.len());
                &mut self.history[*index]
            }
        }
    }

    pub fn extend(&mut self, labels: &BitSet) {
        if !labels.is_empty() {
            let head_idx = match &mut self.head {
                PathHead::Item(item) => {
                    self.history.push(item.clone());
                    let index = self.history.len() - 1;
                    self.head = PathHead::Index(index);
                    index
                }
                PathHead::Index(index) => *index,
            };
            let mut label = self.tags.borrow_mut();
            for s in labels.iter() {
                label.insert(s, head_idx);
            }
        }
    }

    pub fn extend_with<T: Into<PathItem>>(
        &mut self, item: T, labels: &BitSet, is_label_path: bool,
    ) {
        let path_item = item.into();
        if is_label_path && labels.is_empty() {
            self.head = PathHead::Item(path_item);
        } else {
            self.history.push(path_item);
            self.head = PathHead::Index(self.history.len() - 1);
            self.extend(labels);
        }
    }

    // We should 1. remove original head tag, 2. modify head; 3. add new head tag
    // Now we skip step 1 since compiler will attach the same tag for either original head or new head.
    pub fn modify_head_with<T: Into<GraphElement>>(&mut self, element: T, labels: &BitSet) {
        let head = self.head_mut();
        *head = PathItem::OnGraph(element.into());
        self.extend(labels);
    }

    pub fn remove_tag(&mut self, labels: &BitSet, _is_label_path: bool) {
        let mut label = self.tags.borrow_mut();
        for s in labels {
            if let Some(path_idx) = label.get(s) {
                if *path_idx == self.history.len() - 1 {
                    self.head = PathHead::Item(self.history[*path_idx].clone());
                }
                self.history[*path_idx] = PathItem::Empty;
            }
            label.remove(s);
        }
    }

    pub fn get(&self, index: usize) -> Option<&PathItem> {
        if index >= self.history.len() {
            None
        } else {
            Some(&self.history[index])
        }
    }

    pub fn has_tag(&self, label: &Tag) -> bool {
        self.tags.borrow().contains_key(*label as usize)
    }

    pub fn objects(&self) -> &[PathItem] {
        self.history.as_slice()
    }

    pub fn tags(&self) -> &[Tag] {
        unimplemented!("Path#labels")
    }

    pub fn is_simple(&self) -> bool {
        let mut set = HashSet::new();
        for e in self.history.iter() {
            if let PathItem::OnGraph(e) = e {
                if !set.insert(e.id()) {
                    return false;
                }
            }
        }
        true
    }

    pub fn select(&self, label: &Tag) -> Option<&PathItem> {
        let tags = self.tags.borrow();
        if let Some(idx) = tags.get(*label as usize) {
            return self.history.get(*idx);
        }
        None
    }

    pub fn sub_path(&self, _from_label: &str, _to_label: &str) -> Self {
        unimplemented!()
    }

    pub fn finalize(self) -> ResultPath {
        ResultPath::new(self.history)
    }

    pub fn length(&self) -> usize {
        self.history.len()
    }
}

impl Debug for Path {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        write!(f, "head {:?}, path[{:?}]", self.head, self.history)
    }
}

impl Encode for Path {
    fn write_to<W: WriteExt>(&self, writer: &mut W) -> io::Result<()> {
        self.history.write_to(writer)?;
        match &self.head {
            PathHead::Item(item) => {
                writer.write_u8(0)?;
                item.write_to(writer)?;
            }
            PathHead::Index(index) => {
                writer.write_u8(1)?;
                writer.write_u64(*index as u64)?;
            }
        }
        let tags = self.tags.borrow();
        // TODO(longbin) `Tag` is typed `u8`, but tags length may exceed this length.
        // This may happen, but is quite impossible.
        // In addition, if we change Tag's type, this would trigger a compile error.
        writer.write_u8(tags.len() as Tag)?;
        for (k, v) in tags.iter() {
            writer.write_u8(k as Tag)?;
            writer.write_u64(*v as u64)?;
        }
        Ok(())
    }
}

impl Decode for Path {
    fn read_from<R: ReadExt>(reader: &mut R) -> io::Result<Self> {
        let history = <Vec<PathItem>>::read_from(reader)?;
        let head_opt = <u8>::read_from(reader)?;
        let head = if head_opt == 0 {
            let item = PathItem::read_from(reader)?;
            PathHead::Item(item)
        } else {
            let index = <u64>::read_from(reader)? as usize;
            PathHead::Index(index)
        };
        let tags_len = <Tag>::read_from(reader)?;
        let mut tags = VecMap::with_capacity(tags_len as usize);
        for _i in 0..tags_len {
            let k = <Tag>::read_from(reader)? as usize;
            let v = <u64>::read_from(reader)? as usize;
            tags.insert(k, v);
        }
        Ok(Path { history, head, tags: RefCell::new(tags) })
    }
}

#[derive(Debug, Clone)]
pub struct ResultPath {
    elements: Vec<PathItem>,
}

impl ResultPath {
    pub fn new(elements: Vec<PathItem>) -> Self {
        ResultPath { elements }
    }
}

impl Deref for ResultPath {
    type Target = Vec<PathItem>;

    fn deref(&self) -> &Self::Target {
        &self.elements
    }
}

impl_as_any!(ResultPath);

// TODO(yyy)
impl Encode for ResultPath {
    fn write_to<W: WriteExt>(&self, _writer: &mut W) -> std::io::Result<()> {
        unimplemented!()
    }
}

impl Decode for ResultPath {
    fn read_from<R: ReadExt>(_reader: &mut R) -> std::io::Result<Self> {
        unimplemented!()
    }
}
